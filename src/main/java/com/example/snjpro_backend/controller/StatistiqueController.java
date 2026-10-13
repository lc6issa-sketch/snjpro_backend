package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.StatistiqueDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.serviceInterface.StatistiqueServiceInterface;
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

@RestController
@RequiredArgsConstructor
@Tag(name = "19. Tableau de bord, statistiques, rapports & exports",
        description = "Indicateurs (F19, US09), statistiques (F20, US12), rapports (F35) et export des données (F21)")
@SecurityRequirement(name = "bearerAuth")
public class StatistiqueController {
    private static final String LECTURE = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";
    private static final String EXPORT_MEMBRES = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final StatistiqueServiceInterface service;

    @GetMapping("/api/tableau-de-bord")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Indicateurs du tableau de bord (limités au périmètre des responsables)")
    public ResponseEntity<TableauDeBord> tableauDeBord() {
        return ResponseEntity.ok(service.tableauDeBord());
    }

    @GetMapping("/api/statistiques")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Statistiques détaillées (filtres : année d'incorporation, cohorte, région, sexe)")
    public ResponseEntity<Statistiques> statistiques(@RequestParam(required = false) Integer annee,
                                                     @RequestParam(required = false) Long cohorteId,
                                                     @RequestParam(required = false) String region,
                                                     @RequestParam(required = false) SexeEnum sexe) {
        return ResponseEntity.ok(service.statistiques(new Filtres(annee, cohorteId, region, sexe)));
    }

    @GetMapping("/api/statistiques/export")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Exporter les statistiques (XLSX, CSV ou PDF)")
    public ResponseEntity<byte[]> exporterStatistiques(@RequestParam(required = false) Integer annee,
                                                       @RequestParam(required = false) Long cohorteId,
                                                       @RequestParam(required = false) String region,
                                                       @RequestParam(required = false) SexeEnum sexe,
                                                       @RequestParam(defaultValue = "XLSX") FormatFichier format) {
        return Telechargement.export(service.exporterStatistiques(new Filtres(annee, cohorteId, region, sexe), format), "statistiques", format);
    }

    // ---------------------------------------------------------------- Rapports

    @GetMapping("/api/rapports")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Rapports disponibles (générés manuellement ou automatiquement chaque mois)")
    public ResponseEntity<List<RapportDTO>> rapports() {
        return ResponseEntity.ok(service.rapports());
    }

    @PostMapping("/api/rapports")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Générer un rapport (effectifs, compétences, mobilisations, profils incomplets) en PDF ou Excel")
    public ResponseEntity<RapportDTO> generer(@Valid @RequestBody DemandeRapport dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.genererRapport(dto));
    }

    @GetMapping("/api/rapports/{id}/telecharger")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Télécharger un rapport")
    public ResponseEntity<byte[]> telechargerRapport(@PathVariable Long id) {
        Fichier f = service.telechargerRapport(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }

    @DeleteMapping("/api/rapports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Supprimer un rapport")
    public ResponseEntity<Void> supprimerRapport(@PathVariable Long id) {
        service.supprimerRapport(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Export des membres

    @PostMapping("/api/membres/export/estimation")
    @PreAuthorize(EXPORT_MEMBRES)
    @Operation(summary = "Nombre de membres et taille estimée de l'export (fenêtre « Exporter les membres »)")
    public ResponseEntity<EstimationExport> estimerExport(@RequestBody Export export) {
        return ResponseEntity.ok(service.estimerExport(export));
    }

    @PostMapping("/api/membres/export")
    @PreAuthorize(EXPORT_MEMBRES)
    @Operation(summary = "Exporter les membres filtrés (format, sections, masquage des données sensibles) — tracé dans l'historique")
    public ResponseEntity<byte[]> exporterMembres(@RequestBody Export export) {
        Fichier f = service.exporterMembres(export);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }
}
