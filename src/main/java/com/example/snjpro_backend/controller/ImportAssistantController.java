package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.ImportAssistantDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.serviceInterface.ImportAssistantServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
@Tag(name = "07. Recherche, import & doublons")
@SecurityRequirement(name = "bearerAuth")
public class ImportAssistantController {

    private final ImportAssistantServiceInterface service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Assistant d'import — étapes 1-2 : déposer le fichier et obtenir la correspondance proposée des colonnes")
    public ResponseEntity<Analyse> analyser(@RequestParam("fichier") MultipartFile fichier, @RequestParam Long cohorteId) {
        return ResponseEntity.ok(service.analyser(fichier, cohorteId));
    }

    @GetMapping
    @Operation(summary = "Imports récents")
    public ResponseEntity<Page<Session>> lister(@PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(service.lister(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Reprendre un import : colonnes, correspondance et aperçu")
    public ResponseEntity<Analyse> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PutMapping("/{id}/correspondance")
    @Operation(summary = "Corriger la correspondance colonne du fichier -> champ SNJPro (champ vide = colonne ignorée)")
    public ResponseEntity<Analyse> correspondance(@PathVariable Long id, @RequestBody CorrespondanceRequest requete) {
        return ResponseEntity.ok(service.definirCorrespondance(id, requete.getCorrespondance()));
    }

    @PostMapping("/{id}/verification")
    @Operation(summary = "Étape 3 : vérifier les données (erreurs avec suggestion, doublons avec score et comparaison)")
    public ResponseEntity<Verification> verifier(@PathVariable Long id) {
        return ResponseEntity.ok(service.verifier(id));
    }

    @PutMapping("/{id}/doublons/{ligne}")
    @Operation(summary = "Décision sur un doublon : IGNORER, FUSIONNER (complète la fiche existante) ou CONSERVER_LES_DEUX")
    public ResponseEntity<DoublonLigne> decider(@PathVariable Long id, @PathVariable int ligne, @RequestBody DecisionRequest requete) {
        return ResponseEntity.ok(service.decider(id, ligne, requete.getDecision()));
    }

    @PutMapping("/{id}/doublons")
    @Operation(summary = "Appliquer la même décision à tous les doublons")
    public ResponseEntity<Verification> deciderTous(@PathVariable Long id, @RequestBody DecisionRequest requete) {
        return ResponseEntity.ok(service.deciderTous(id, requete.getDecision()));
    }

    @PostMapping("/{id}/importer")
    @Operation(summary = "Étape 4 : lancer l'import (lignes en erreur et doublons sans décision ignorés)")
    public ResponseEntity<Resultat> importer(@PathVariable Long id) {
        return ResponseEntity.ok(service.importer(id));
    }

    @GetMapping("/{id}/erreurs/export")
    @Operation(summary = "Télécharger les erreurs et doublons (XLSX, CSV ou PDF) pour corriger le fichier")
    public ResponseEntity<byte[]> exporterErreurs(@PathVariable Long id, @RequestParam(defaultValue = "XLSX") FormatFichier format) {
        Fichier f = service.exporterErreurs(id, format);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }

    @GetMapping("/{id}/rapport")
    @Operation(summary = "Rapport d'import (PDF)")
    public ResponseEntity<byte[]> rapport(@PathVariable Long id) {
        Fichier f = service.rapport(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Abandonner un import en cours (ou supprimer le fichier d'un import terminé)")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
