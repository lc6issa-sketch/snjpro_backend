package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.serviceInterface.CompetenceParcoursSNJServiceInterface;
import com.example.snjpro_backend.serviceInterface.LocalisationServiceInterface;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import com.example.snjpro_backend.serviceInterface.SituationProfessionnelleServiceInterface;
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

/**
 * Profil numérique d'un membre, géré par l'administration et les responsables (dans leur périmètre).
 * Les membres utilisent /api/mon-profil pour leur propre fiche.
 */
@RestController
@RequestMapping("/api/membres/{membreId}/profil")
@RequiredArgsConstructor
@Tag(name = "05. Profil membre", description = "Profil complet, situation professionnelle, localisation, compétences et parcours SNJ")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#membreId)")
public class ProfilController {

    private final MembreServiceInterface membreService;
    private final SituationProfessionnelleServiceInterface situationService;
    private final LocalisationServiceInterface localisationService;
    private final CompetenceParcoursSNJServiceInterface competenceParcoursService;

    @GetMapping
    @Operation(summary = "Profil numérique complet (avec taux de complétude)")
    public ResponseEntity<ProfilMembreDTO> profilComplet(@PathVariable Long membreId) {
        return ResponseEntity.ok(membreService.obtenirProfilComplet(membreId));
    }

    // ---------- Situation professionnelle ----------

    @PostMapping("/situation-professionnelle")
    @Operation(summary = "Enregistrer la situation professionnelle")
    public ResponseEntity<SituationProfessionnelleDTO> enregistrerSituation(
            @PathVariable Long membreId, @Valid @RequestBody SituationProfessionnelleDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(situationService.enregistrer(membreId, dto));
    }

    @PutMapping("/situation-professionnelle")
    @Operation(summary = "Modifier (ou créer) la situation professionnelle")
    public ResponseEntity<SituationProfessionnelleDTO> modifierSituation(
            @PathVariable Long membreId, @Valid @RequestBody SituationProfessionnelleDTO dto) {
        return ResponseEntity.ok(situationService.modifier(membreId, dto));
    }

    @GetMapping("/situation-professionnelle")
    @Operation(summary = "Obtenir la situation professionnelle")
    public ResponseEntity<SituationProfessionnelleDTO> obtenirSituation(@PathVariable Long membreId) {
        return ResponseEntity.ok(situationService.obtenir(membreId));
    }

    // ---------- Localisation ----------

    @PostMapping("/localisation")
    @Operation(summary = "Enregistrer la localisation")
    public ResponseEntity<LocalisationDTO> enregistrerLocalisation(
            @PathVariable Long membreId, @Valid @RequestBody LocalisationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(localisationService.enregistrer(membreId, dto));
    }

    @PutMapping("/localisation")
    @Operation(summary = "Modifier (ou créer) la localisation")
    public ResponseEntity<LocalisationDTO> modifierLocalisation(
            @PathVariable Long membreId, @Valid @RequestBody LocalisationDTO dto) {
        return ResponseEntity.ok(localisationService.modifier(membreId, dto));
    }

    @GetMapping("/localisation")
    @Operation(summary = "Obtenir la localisation")
    public ResponseEntity<LocalisationDTO> obtenirLocalisation(@PathVariable Long membreId) {
        return ResponseEntity.ok(localisationService.obtenir(membreId));
    }

    // ---------- Compétences ----------

    @PostMapping("/competences")
    @Operation(summary = "Ajouter une compétence")
    public ResponseEntity<CompetenceDTO> ajouterCompetence(
            @PathVariable Long membreId, @Valid @RequestBody CompetenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competenceParcoursService.ajouterCompetence(membreId, dto));
    }

    @GetMapping("/competences")
    @Operation(summary = "Lister les compétences")
    public ResponseEntity<List<CompetenceDTO>> listerCompetences(@PathVariable Long membreId) {
        return ResponseEntity.ok(competenceParcoursService.obtenirCompetencesMembre(membreId));
    }

    @PutMapping("/competences/{competenceId}")
    @Operation(summary = "Modifier une compétence")
    public ResponseEntity<CompetenceDTO> modifierCompetence(
            @PathVariable Long membreId, @PathVariable Long competenceId, @Valid @RequestBody CompetenceDTO dto) {
        return ResponseEntity.ok(competenceParcoursService.modifierCompetence(membreId, competenceId, dto));
    }

    @DeleteMapping("/competences/{competenceId}")
    @Operation(summary = "Supprimer une compétence")
    public ResponseEntity<Void> supprimerCompetence(@PathVariable Long membreId, @PathVariable Long competenceId) {
        competenceParcoursService.supprimerCompetence(membreId, competenceId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Parcours SNJ ----------

    @PostMapping("/parcours")
    @Operation(summary = "Ajouter un élément de parcours SNJ")
    public ResponseEntity<ParcoursSNJDTO> ajouterParcours(
            @PathVariable Long membreId, @Valid @RequestBody ParcoursSNJDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(competenceParcoursService.ajouterParcours(membreId, dto));
    }

    @GetMapping("/parcours")
    @Operation(summary = "Lister le parcours SNJ (du plus récent au plus ancien)")
    public ResponseEntity<List<ParcoursSNJDTO>> listerParcours(@PathVariable Long membreId) {
        return ResponseEntity.ok(competenceParcoursService.obtenirParcoursMembre(membreId));
    }

    @PutMapping("/parcours/{parcoursId}")
    @Operation(summary = "Modifier un élément de parcours")
    public ResponseEntity<ParcoursSNJDTO> modifierParcours(
            @PathVariable Long membreId, @PathVariable Long parcoursId, @Valid @RequestBody ParcoursSNJDTO dto) {
        return ResponseEntity.ok(competenceParcoursService.modifierParcours(membreId, parcoursId, dto));
    }

    @DeleteMapping("/parcours/{parcoursId}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Supprimer un élément de parcours")
    public ResponseEntity<Void> supprimerParcours(@PathVariable Long membreId, @PathVariable Long parcoursId) {
        competenceParcoursService.supprimerParcours(membreId, parcoursId);
        return ResponseEntity.noContent().build();
    }
}
