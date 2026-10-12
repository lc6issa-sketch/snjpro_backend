package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.ActiviteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActivite;
import com.example.snjpro_backend.serviceInterface.ActiviteServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "17. Activités & présences", description = "Activités, QR code de présence renouvelé toutes les 30 s, pointage des membres (F26, US14)")
@SecurityRequirement(name = "bearerAuth")
public class ActiviteController {
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final ActiviteServiceInterface service;

    @GetMapping("/api/activites")
    @PreAuthorize(GESTION)
    @Operation(summary = "Lister les activités (filtres : statut, année, mois)")
    public ResponseEntity<List<Resume>> lister(@RequestParam(required = false) StatutActivite statut,
                                               @RequestParam(required = false) Integer annee,
                                               @RequestParam(required = false) Integer mois) {
        return ResponseEntity.ok(service.lister(statut, annee, mois));
    }

    @GetMapping("/api/activites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Détail d'une activité et compteurs (présents, inscrits, arrivées de la dernière heure…)")
    public ResponseEntity<Resume> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping("/api/activites")
    @PreAuthorize(GESTION)
    @Operation(summary = "Créer une activité (un QR code de présence est généré automatiquement)")
    public ResponseEntity<Resume> creer(@Valid @RequestBody Edition dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PutMapping("/api/activites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier une activité")
    public ResponseEntity<Resume> modifier(@PathVariable Long id, @Valid @RequestBody Edition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PostMapping("/api/activites/{id}/terminer")
    @PreAuthorize(GESTION)
    @Operation(summary = "Terminer l'activité (désactive le QR code)")
    public ResponseEntity<Resume> terminer(@PathVariable Long id) {
        return ResponseEntity.ok(service.terminer(id));
    }

    @DeleteMapping("/api/activites/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer une activité sans présence")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/activites/{id}/qr")
    @PreAuthorize(GESTION)
    @Operation(summary = "Code de présence courant (contenu du QR + code à 6 caractères, renouvelé toutes les 30 s)")
    public ResponseEntity<CodeQr> qr(@PathVariable Long id) {
        return ResponseEntity.ok(service.codeCourant(id));
    }

    @GetMapping("/api/activites/{id}/projection")
    @PreAuthorize(GESTION)
    @Operation(summary = "Données de l'écran de projection : QR courant, compteur de présents, dernières arrivées")
    public ResponseEntity<Projection> projection(@PathVariable Long id) {
        return ResponseEntity.ok(service.projection(id));
    }

    @GetMapping("/api/activites/{id}/presences")
    @PreAuthorize(GESTION)
    @Operation(summary = "Derniers pointages")
    public ResponseEntity<Page<PresenceDTO>> presences(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.presences(id, pageable));
    }

    @PostMapping("/api/activites/{id}/presences")
    @PreAuthorize(GESTION)
    @Operation(summary = "Saisir une présence manuellement (membre sans téléphone…)")
    public ResponseEntity<PresenceDTO> saisir(@PathVariable Long id, @Valid @RequestBody SaisieManuelle dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.saisirPresence(id, dto));
    }

    @DeleteMapping("/api/activites/{id}/presences/{presenceId}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Annuler une présence enregistrée par erreur")
    public ResponseEntity<Void> supprimerPresence(@PathVariable Long id, @PathVariable Long presenceId) {
        service.supprimerPresence(id, presenceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/activites/{id}/presences/export")
    @PreAuthorize(GESTION)
    @Operation(summary = "Exporter la liste des présences (XLSX, CSV ou PDF)")
    public ResponseEntity<byte[]> exporter(@PathVariable Long id, @RequestParam(defaultValue = "XLSX") FormatFichier format) {
        return Telechargement.export(service.exporterPresences(id, format), "presences_activite_" + id, format);
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/activites/du-jour")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Activités du jour (écran « Scanner une présence »)")
    public ResponseEntity<List<Resume>> duJour() {
        return ResponseEntity.ok(service.activitesDuJour());
    }

    @PostMapping("/api/mon-espace/presences")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Enregistrer ma présence (QR code scanné ou code saisi) — ajoutée à mon parcours SNJ")
    public ResponseEntity<ConfirmationPresence> pointer(@RequestBody Pointage dto) {
        return ResponseEntity.ok(service.pointer(dto));
    }

    @GetMapping("/api/mon-espace/presences")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes présences")
    public ResponseEntity<List<PresenceDTO>> mesPresences() {
        return ResponseEntity.ok(service.mesPresences());
    }
}
