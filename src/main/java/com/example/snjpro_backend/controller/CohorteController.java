package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.CohorteDTO;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.serviceInterface.CohorteServiceInterface;
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
@RequestMapping("/api/cohortes")
@RequiredArgsConstructor
@Tag(name = "03. Cohortes", description = "Création, modification, consultation et archivage des cohortes")
@SecurityRequirement(name = "bearerAuth")
public class CohorteController {

    private final CohorteServiceInterface cohorteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Créer une cohorte")
    public ResponseEntity<CohorteDTO> creer(@Valid @RequestBody CohorteDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cohorteService.creerCohorte(dto));
    }

    @GetMapping
    @Operation(summary = "Lister les cohortes (filtre optionnel par statut / nom)")
    public ResponseEntity<List<CohorteDTO>> listerTous(@RequestParam(required = false) StatutCohorteEnum statut,
                                                       @RequestParam(required = false) String nom) {
        return ResponseEntity.ok(cohorteService.obtenirToutesLesCohortes(statut, nom));
    }

    @GetMapping("/compteurs")
    @Operation(summary = "Nombre de cohortes par statut (onglets Toutes / En formation / Libérées / En réserve / Archivées)")
    public ResponseEntity<Map<String, Long>> compteurs() {
        return ResponseEntity.ok(cohorteService.compteurs());
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Changer le statut (en formation, en réserve, libérée, archivée) avec date d'effet et motif ; les membres peuvent être notifiés")
    public ResponseEntity<CohorteDTO> changerStatut(@PathVariable Long id, @RequestParam StatutCohorteEnum statut,
                                                    @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateEffet,
                                                    @RequestParam(required = false) String motif,
                                                    @RequestParam(defaultValue = "true") boolean notifierMembres) {
        return ResponseEntity.ok(cohorteService.changerStatut(id, statut, dateEffet, motif, notifierMembres));
    }

    @GetMapping("/{id}/tableau-de-bord")
    @Operation(summary = "Détail d'une cohorte : recrues, actifs, profils à jour, mobilisés, situation actuelle des membres")
    public ResponseEntity<Map<String, Object>> tableauDeBord(@PathVariable Long id) {
        return ResponseEntity.ok(cohorteService.tableauDeBord(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une cohorte")
    public ResponseEntity<CohorteDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(cohorteService.obtenirCohorte(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Modifier une cohorte")
    public ResponseEntity<CohorteDTO> modifier(@PathVariable Long id, @Valid @RequestBody CohorteDTO dto) {
        return ResponseEntity.ok(cohorteService.modifierCohorte(id, dto));
    }

    @PatchMapping("/{id}/archiver")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Archiver une cohorte")
    public ResponseEntity<CohorteDTO> archiver(@PathVariable Long id) {
        return ResponseEntity.ok(cohorteService.archiverCohorte(id));
    }

    @PatchMapping("/{id}/reactiver")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Réactiver une cohorte archivée")
    public ResponseEntity<CohorteDTO> reactiver(@PathVariable Long id) {
        return ResponseEntity.ok(cohorteService.reactiverCohorte(id));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Nombre de membres d'une cohorte")
    public ResponseEntity<Map<String, Object>> stats(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("cohorteId", id, "nombreMembres", cohorteService.compterMembresCohorte(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Supprimer une cohorte vide")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        cohorteService.supprimerCohorte(id);
        return ResponseEntity.noContent().build();
    }
}
