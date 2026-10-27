package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.CompetenceReferentielDTOs.*;
import com.example.snjpro_backend.serviceInterface.CompetenceReferentielServiceInterface;
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
@Tag(name = "08. Référentiel des compétences", description = "Compétences et catégories, nombre de membres et niveau moyen (F11, US07)")
@SecurityRequirement(name = "bearerAuth")
public class CompetenceController {
    private static final String LECTURE = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')";

    private final CompetenceReferentielServiceInterface service;

    @GetMapping("/api/competences")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Lister les compétences (filtres : catégorie, recherche ; tri : MEMBRES, NOM, RECENTES)",
            description = "Responsable de cohorte : par défaut, seules les compétences des membres de sa cohorte ; toutes=true pour l'ensemble des cohortes.")
    public ResponseEntity<List<Competence>> lister(@RequestParam(required = false) String categorie,
                                                   @RequestParam(required = false) String recherche,
                                                   @RequestParam(defaultValue = "MEMBRES") String tri,
                                                   @RequestParam(defaultValue = "false") boolean toutes) {
        return ResponseEntity.ok(service.lister(categorie, recherche, tri, toutes));
    }

    @GetMapping("/api/competences/categories")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Catégories avec le nombre de compétences et de membres")
    public ResponseEntity<List<Categorie>> categories(@RequestParam(defaultValue = "false") boolean toutes) {
        return ResponseEntity.ok(service.categories(toutes));
    }

    @GetMapping("/api/competences/{id}")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Obtenir une compétence")
    public ResponseEntity<Competence> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @GetMapping("/api/competences/{id}/membres")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Membres possédant cette compétence (dans mon périmètre)")
    public ResponseEntity<List<Detenteur>> detenteurs(@PathVariable Long id) {
        return ResponseEntity.ok(service.detenteurs(id));
    }

    @PostMapping("/api/competences")
    @PreAuthorize(GESTION)
    @Operation(summary = "Ajouter une compétence au référentiel")
    public ResponseEntity<Competence> creer(@Valid @RequestBody Edition dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PutMapping("/api/competences/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier une compétence (le renommage est répercuté sur les fiches)")
    public ResponseEntity<Competence> modifier(@PathVariable Long id, @Valid @RequestBody Edition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @DeleteMapping("/api/competences/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer une compétence non utilisée")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/competences/synchroniser")
    @PreAuthorize(GESTION)
    @Operation(summary = "Ajouter au référentiel les compétences saisies sur les fiches (après un import par exemple)")
    public ResponseEntity<Map<String, Integer>> synchroniser() {
        return ResponseEntity.ok(Map.of("ajoutees", service.synchroniser()));
    }

    @GetMapping("/api/mon-espace/competences/suggestions")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Suggestions de compétences pour le membre connecté")
    public ResponseEntity<List<Competence>> suggestions(@RequestParam(defaultValue = "5") int limite) {
        return ResponseEntity.ok(service.suggestionsPourMoi(limite));
    }

    @GetMapping("/api/mon-espace/competences/referentiel")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Rechercher dans le référentiel (écran « Ajouter une compétence »)")
    public ResponseEntity<List<Competence>> referentielMembre(@RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(null, recherche, "MEMBRES", true));
    }
}
