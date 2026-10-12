package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.MembreResponseDTO;
import com.example.snjpro_backend.entity.Historique;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.serviceImpl.GenerateurFichiers;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "10. Historique", description = "Traçabilité des opérations importantes (F34) : journal, détail des champs modifiés, restauration")
@SecurityRequirement(name = "bearerAuth")
public class HistoriqueController {

    private final HistoriqueServiceInterface historiqueService;
    private final MembreServiceInterface membreService;
    private final GenerateurFichiers generateur;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OperationDTO {
        private Long id;
        private String action;
        private String entite;
        private Long entiteId;
        private String details;
        private String utilisateur;
        private String adresseIp;
        private LocalDateTime dateAction;
        private List<Map<String, String>> champsModifies;
        /** true si les anciennes valeurs peuvent être restaurées (modification d'une fiche membre). */
        private boolean restaurable;
    }

    @GetMapping("/api/historique")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Journal des opérations (filtres : utilisateur, action, entité, période, recherche)",
            description = "Compatibilité : entite + entiteId donnent l'historique d'un élément.")
    public ResponseEntity<Page<OperationDTO>> lister(
            @RequestParam(required = false) String utilisateur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) Long entiteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche,
            @PageableDefault(size = 20, sort = "dateAction", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Historique> page = entiteId != null
                ? historiqueService.lister(entite, entiteId, null, pageable)
                : historiqueService.rechercher(utilisateur, action, entite, du, au, recherche, pageable);
        return ResponseEntity.ok(page.map(this::toDTO));
    }

    @GetMapping("/api/historique/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Détail d'une opération (champs modifiés : ancienne / nouvelle valeur)")
    public ResponseEntity<OperationDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(toDTO(historiqueService.obtenir(id)));
    }

    @PostMapping("/api/historique/{id}/restaurer")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Restaurer les valeurs précédant une modification de fiche membre (tracé dans l'historique)")
    public ResponseEntity<MembreResponseDTO> restaurer(@PathVariable Long id) {
        Historique h = historiqueService.obtenir(id);
        OperationDTO op = toDTO(h);
        if (!op.isRestaurable())
            throw new IllegalArgumentException("Cette opération ne contient pas de valeurs restaurables.");
        Map<String, String> valeurs = new LinkedHashMap<>();
        op.getChampsModifies().forEach(c -> valeurs.put(c.get("champ"), c.get("ancienne")));
        return ResponseEntity.ok(membreService.restaurer(h.getEntiteId(), valeurs, id));
    }

    @GetMapping("/api/historique/export")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Exporter le journal filtré (XLSX, CSV ou PDF ; 10 000 lignes maximum)")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) String utilisateur,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "XLSX") FormatFichier format) {
        List<List<String>> lignes = historiqueService.rechercher(utilisateur, action, entite, du, au, recherche, PageRequest.of(0, 10_000))
                .map(h -> List.of(h.getDateAction().toString(), nvl(h.getUtilisateur()), h.getAction(), h.getEntite(),
                        h.getEntiteId() == null ? "" : h.getEntiteId().toString(), nvl(h.getDetails()), nvl(h.getAdresseIp())))
                .getContent();
        historiqueService.enregistrer("EXPORT", "HISTORIQUE", null, lignes.size() + " opération(s) · " + format);
        byte[] contenu = generateur.tableau(new GenerateurFichiers.Tableau("Journal des opérations",
                List.of("Date", "Utilisateur", "Action", "Élément", "Id", "Détails", "Adresse IP"), lignes), format);
        return Telechargement.export(contenu, "historique", format);
    }

    @GetMapping("/api/membres/{membreId}/historique")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#membreId)")
    @Operation(summary = "Historique d'un membre (onglet « Historique » du profil)")
    public ResponseEntity<Page<OperationDTO>> historiqueMembre(@PathVariable Long membreId,
                                                              @PageableDefault(size = 20, sort = "dateAction", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(historiqueService.lister("MEMBRE", membreId, null, pageable).map(this::toDTO));
    }

    private OperationDTO toDTO(Historique h) {
        List<Map<String, String>> champs = historiqueService.champsModifies(h);
        return OperationDTO.builder()
                .id(h.getId()).action(h.getAction()).entite(h.getEntite()).entiteId(h.getEntiteId()).details(h.getDetails())
                .utilisateur(h.getUtilisateur()).adresseIp(masquerIp(h.getAdresseIp())).dateAction(h.getDateAction())
                .champsModifies(champs)
                .restaurable("MEMBRE".equals(h.getEntite()) && h.getEntiteId() != null && !champs.isEmpty())
                .build();
    }

    private static String masquerIp(String ip) {
        if (ip == null || !ip.contains(".")) return ip;
        String[] p = ip.split("\\.");
        return p.length == 4 ? p[0] + "." + p[1] + ".•••." + p[3] : ip;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
