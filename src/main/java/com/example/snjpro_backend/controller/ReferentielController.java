package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.enumeration.*;
import com.example.snjpro_backend.enumeration.Enumerations;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.repository.SituationProfessionnelleRepository;
import com.example.snjpro_backend.serviceInterface.CompetenceParcoursSNJServiceInterface;
import com.example.snjpro_backend.serviceInterface.LocalisationServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Map;

/**
 * Listes de valeurs utilisées par les formulaires et les filtres (web et mobile).
 */
@RestController
@RequestMapping("/api/referentiels")
@RequiredArgsConstructor
@Tag(name = "09. Référentiels", description = "Listes de valeurs pour les formulaires et filtres")
@SecurityRequirement(name = "bearerAuth")
public class ReferentielController {

    /** Régions administratives du Mali (réorganisation territoriale de 2023) et District de Bamako. */
    private static final List<String> REGIONS_MALI = List.of(
            "Bamako", "Kayes", "Koulikoro", "Sikasso", "Ségou", "Mopti", "Tombouctou", "Gao", "Kidal",
            "Taoudénit", "Ménaka", "Nioro", "Kita", "Dioïla", "Nara", "Bougouni", "Koutiala", "San",
            "Douentza", "Bandiagara");

    /** Centres d'instruction du SNJ (complétés par ceux saisis sur les cohortes). */
    private static final List<String> CENTRES = List.of("Centre d'instruction de Ségou", "Centre d'instruction de Kati",
            "Centre d'instruction de Koulikoro", "Centre d'instruction de Sikasso", "Centre d'instruction de Kayes");
    private static final List<String> DIPLOMES = com.example.snjpro_backend.serviceImpl.Diplomes.REFERENTIEL;
    private static final List<String> GROUPES_SANGUINS = List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    private static final List<String> CATEGORIES = List.of("FONCTIONNAIRE", "NON_FONCTIONNAIRE");

    private final CohorteRepository cohorteRepository;
    private final LocalisationServiceInterface localisationService;
    private final CompetenceParcoursSNJServiceInterface competenceParcoursService;
    private final SituationProfessionnelleRepository situationRepository;

    @GetMapping("/regions-mali")
    @Operation(summary = "Régions administratives du Mali (suggestions de saisie)")
    public ResponseEntity<List<String>> regionsMali() {
        return ResponseEntity.ok(REGIONS_MALI);
    }

    @GetMapping("/regions")
    @Operation(summary = "Régions effectivement renseignées dans les profils")
    public ResponseEntity<List<String>> regions() {
        return ResponseEntity.ok(localisationService.obtenirToutesLesRegions());
    }

    @GetMapping("/competences")
    @Operation(summary = "Compétences déjà enregistrées (pour l'autocomplétion et les filtres)")
    public ResponseEntity<List<String>> competences() {
        return ResponseEntity.ok(competenceParcoursService.obtenirIntitulesCompetences());
    }

    @GetMapping("/professions")
    @Operation(summary = "Professions déjà enregistrées")
    public ResponseEntity<List<String>> professions() {
        return ResponseEntity.ok(situationRepository.findDistinctProfessions());
    }

    @GetMapping("/centres")
    @Operation(summary = "Centres d'instruction")
    public ResponseEntity<List<String>> centres() {
        TreeSet<String> centres = new TreeSet<>(CENTRES);
        centres.addAll(cohorteRepository.findDistinctCentres());
        return ResponseEntity.ok(List.copyOf(centres));
    }

    @GetMapping("/diplomes")
    @Operation(summary = "Niveaux de diplôme")
    public ResponseEntity<List<String>> diplomes() {
        return ResponseEntity.ok(DIPLOMES);
    }

    @GetMapping("/groupes-sanguins")
    @Operation(summary = "Groupes sanguins")
    public ResponseEntity<List<String>> groupesSanguins() {
        return ResponseEntity.ok(GROUPES_SANGUINS);
    }

    @GetMapping("/enumerations")
    @Operation(summary = "Valeurs possibles des énumérations (sexe, statuts, niveaux...)")
    public ResponseEntity<Map<String, Object>> enumerations() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sexe", SexeEnum.values());
        map.put("statutMembre", StatutMembreEnum.values());
        map.put("statutCohorte", StatutCohorteEnum.values());
        map.put("statutProfessionnel", StatutProfessionnelEnum.values());
        map.put("niveauCompetence", NiveauCompetenceEnum.values());
        map.put("role", RoleEnum.values());
        map.put("categorie", CATEGORIES);
        map.put("groupeSanguin", GROUPES_SANGUINS);
        map.put("diplome", DIPLOMES);
        map.put("statutMobilisation", Enumerations.StatutMobilisation.values());
        map.put("reponseMobilisation", Enumerations.ReponseMobilisation.values());
        map.put("typeNotification", Enumerations.TypeNotification.values());
        map.put("cibleNotification", Enumerations.CibleNotification.values());
        map.put("categorieActualite", Enumerations.CategorieActualite.values());
        map.put("statutActualite", Enumerations.StatutActualite.values());
        map.put("typeOpportunite", Enumerations.TypeOpportunite.values());
        map.put("statutOpportunite", Enumerations.StatutOpportunite.values());
        map.put("statutCandidature", Enumerations.StatutCandidature.values());
        map.put("typeDocument", Enumerations.TypeDocument.values());
        map.put("visibiliteDocument", Enumerations.VisibiliteDocument.values());
        map.put("typeDemande", Enumerations.TypeDemande.values());
        map.put("statutDemande", Enumerations.StatutDemande.values());
        map.put("statutActivite", Enumerations.StatutActivite.values());
        map.put("modePresence", Enumerations.ModePresence.values());
        map.put("decisionDoublon", Enumerations.DecisionDoublon.values());
        map.put("formatFichier", Enumerations.FormatFichier.values());
        map.put("typeRapport", Enumerations.TypeRapport.values());
        return ResponseEntity.ok(map);
    }
}
