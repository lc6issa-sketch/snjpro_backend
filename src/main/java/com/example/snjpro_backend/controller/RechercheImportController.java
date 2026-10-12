package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DoublonGroupeDTO;
import com.example.snjpro_backend.dto.ImportResultDTO;
import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.dto.MembreResponseDTO;
import com.example.snjpro_backend.serviceInterface.RechercheImportServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "07. Recherche, import & doublons", description = "Recherche avancée, import Excel/CSV, validation et gestion des doublons")
@SecurityRequirement(name = "bearerAuth")
public class RechercheImportController {

    private static final String GESTIONNAIRES = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final RechercheImportServiceInterface rechercheImportService;

    @GetMapping("/membres/filtres")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Recherche avancée (critères combinables en paramètres)",
            description = "Tous les critères sont facultatifs et combinés (ET). Les résultats sont limités au périmètre de l'utilisateur.")
    public ResponseEntity<Page<MembreResponseDTO>> rechercheAvanceeGet(
            @ParameterObject MembreFilterDTO filter,
            @ParameterObject @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        return ResponseEntity.ok(rechercheImportService.rechercheAvancee(filter, pageable));
    }

    @PostMapping("/membres/filtres")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Recherche avancée (critères dans le corps JSON)")
    public ResponseEntity<Page<MembreResponseDTO>> rechercheAvancee(
            @RequestBody(required = false) MembreFilterDTO filter,
            @ParameterObject @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        if (filter == null) filter = new MembreFilterDTO();
        return ResponseEntity.ok(rechercheImportService.rechercheAvancee(filter, pageable));
    }

    @PostMapping(value = "/import/membres", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Importer des membres depuis un fichier Excel (.xlsx/.xls) ou CSV",
            description = "Colonnes obligatoires : prenom, nom, sexe. Les colonnes sont vérifiées, chaque ligne est validée, "
                    + "les doublons (base et fichier) sont détectés et ignorés. Avec simulation=true, rien n'est enregistré : "
                    + "le rapport permet de corriger le fichier avant l'import réel.")
    public ResponseEntity<ImportResultDTO> importerMembres(
            @Parameter(description = "Fichier .xlsx, .xls ou .csv") @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("cohorteId") Long cohorteId,
            @RequestParam(value = "simulation", defaultValue = "false") boolean simulation,
            Authentication authentication) {
        return ResponseEntity.ok(rechercheImportService.importerFichier(fichier, cohorteId, authentication.getName(), simulation));
    }

    @GetMapping("/import/modele")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Télécharger le modèle CSV d'import (avec exemples)")
    public ResponseEntity<byte[]> modeleImport() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele_import_membres.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(rechercheImportService.modeleImportCsv());
    }

    @GetMapping("/membres/doublons")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Détecter les fiches potentiellement dupliquées",
            description = "Critères : même téléphone (0.95), même email (0.9), même nom+prénom+date de naissance (0.9), "
                    + "même nom+prénom (0.6). Les inversions nom/prénom et les accents sont pris en compte.")
    public ResponseEntity<List<DoublonGroupeDTO>> detecterDoublons() {
        return ResponseEntity.ok(rechercheImportService.detecterDoublons());
    }

    @PostMapping("/membres/doublons/fusionner")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Fusionner un doublon dans une fiche principale",
            description = "Complète la fiche principale avec les informations manquantes du doublon "
                    + "(coordonnées, situation, localisation, compétences, parcours), puis archive le doublon.")
    public ResponseEntity<MembreResponseDTO> fusionnerMembres(@RequestParam Long membreIdPrincipal,
                                                              @RequestParam Long membreIdDoublon) {
        return ResponseEntity.ok(rechercheImportService.fusionnerMembres(membreIdPrincipal, membreIdDoublon));
    }
}
