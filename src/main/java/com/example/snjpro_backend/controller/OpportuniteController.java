package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.OpportuniteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutCandidature;
import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import com.example.snjpro_backend.enumeration.Enumerations.TypeOpportunite;
import com.example.snjpro_backend.serviceInterface.OpportuniteServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "14. Opportunités", description = "Offres d'emploi, de stage et de formation (F30) et candidatures des membres")
@SecurityRequirement(name = "bearerAuth")
public class OpportuniteController {
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'PARTENAIRE')";

    private final OpportuniteServiceInterface service;

    @GetMapping("/api/opportunites")
    @PreAuthorize(GESTION)
    @Operation(summary = "Lister les opportunités (filtres : type, statut, recherche)")
    public ResponseEntity<List<Resume>> lister(@RequestParam(required = false) TypeOpportunite type,
                                               @RequestParam(required = false) StatutOpportunite statut,
                                               @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(type, statut, recherche));
    }

    @GetMapping("/api/opportunites/compteurs")
    @PreAuthorize(GESTION)
    @Operation(summary = "Nombre d'opportunités par type")
    public ResponseEntity<Compteurs> compteurs() {
        return ResponseEntity.ok(service.compteurs());
    }

    @GetMapping("/api/opportunites/correspondances")
    @PreAuthorize(GESTION)
    @Operation(summary = "Nombre de membres correspondant à des compétences (et une région) avant publication")
    public ResponseEntity<Map<String, Object>> correspondances(@RequestParam(required = false) List<String> competences,
                                                               @RequestParam(required = false) String region) {
        return ResponseEntity.ok(service.estimerCorrespondances(competences, region));
    }

    @GetMapping("/api/opportunites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Détail d'une opportunité")
    public ResponseEntity<Detail> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping("/api/opportunites")
    @PreAuthorize(GESTION)
    @Operation(summary = "Créer une opportunité (publier = true pour la publier immédiatement)")
    public ResponseEntity<Detail> creer(@Valid @RequestBody Edition dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PutMapping("/api/opportunites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier une opportunité")
    public ResponseEntity<Detail> modifier(@PathVariable Long id, @Valid @RequestBody Edition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PostMapping("/api/opportunites/{id}/publier")
    @PreAuthorize(GESTION)
    @Operation(summary = "Publier un brouillon (notifie les membres ciblés)")
    public ResponseEntity<Detail> publier(@PathVariable Long id) {
        return ResponseEntity.ok(service.publier(id));
    }

    @PostMapping("/api/opportunites/{id}/cloturer")
    @PreAuthorize(GESTION)
    @Operation(summary = "Clôturer une opportunité")
    public ResponseEntity<Detail> cloturer(@PathVariable Long id) {
        return ResponseEntity.ok(service.cloturer(id));
    }

    @DeleteMapping("/api/opportunites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer une opportunité sans candidature")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/opportunites/{id}/candidatures")
    @PreAuthorize(GESTION)
    @Operation(summary = "Candidatures triées par correspondance avec le profil")
    public ResponseEntity<List<CandidatureDTO>> candidatures(@PathVariable Long id) {
        return ResponseEntity.ok(service.candidatures(id));
    }

    @PatchMapping("/api/opportunites/candidatures/{candidatureId}/statut")
    @PreAuthorize(GESTION)
    @Operation(summary = "Changer le statut d'une candidature (le membre est notifié)")
    public ResponseEntity<CandidatureDTO> statutCandidature(@PathVariable Long candidatureId,
                                                            @RequestParam StatutCandidature statut) {
        return ResponseEntity.ok(service.changerStatutCandidature(candidatureId, statut));
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/opportunites")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Opportunités ouvertes, triées par correspondance avec mon profil (pourVous = recommandées)")
    public ResponseEntity<List<Resume>> pourMoi(@RequestParam(required = false) TypeOpportunite type,
                                                @RequestParam(defaultValue = "false") boolean pourVous) {
        return ResponseEntity.ok(service.pourMoi(type, pourVous));
    }

    @GetMapping("/api/mon-espace/opportunites/candidatures")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes candidatures")
    public ResponseEntity<List<CandidatureDTO>> mesCandidatures() {
        return ResponseEntity.ok(service.mesCandidatures());
    }

    @GetMapping("/api/mon-espace/opportunites/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Détail d'une opportunité (correspondance avec mon profil)")
    public ResponseEntity<Detail> consulter(@PathVariable Long id) {
        return ResponseEntity.ok(service.consulter(id));
    }

    @PostMapping("/api/mon-espace/opportunites/{id}/candidature")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Postuler avec mon CV numérique")
    public ResponseEntity<CandidatureDTO> postuler(@PathVariable Long id, @RequestBody(required = false) Postuler dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.postuler(id, dto));
    }

    @PostMapping("/api/mon-espace/opportunites/{id}/favori")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Ajouter ou retirer des favoris")
    public ResponseEntity<Resume> favori(@PathVariable Long id) {
        return ResponseEntity.ok(service.basculerFavori(id));
    }
}
