package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.MobilisationDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import com.example.snjpro_backend.serviceInterface.MobilisationServiceInterface;
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
@Tag(name = "11. Mobilisation", description = "Sélection, sollicitation et suivi des membres mobilisés (F24, F25, US08)")
@SecurityRequirement(name = "bearerAuth")
public class MobilisationController {
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final MobilisationServiceInterface service;

    @GetMapping("/api/mobilisations")
    @PreAuthorize(GESTION)
    @Operation(summary = "Lister les mobilisations (filtres : statut, recherche) avec le décompte des réponses")
    public ResponseEntity<List<Resume>> lister(@RequestParam(required = false) StatutMobilisation statut,
                                               @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(statut, recherche));
    }

    @GetMapping("/api/mobilisations/statistiques")
    @PreAuthorize(GESTION)
    @Operation(summary = "Indicateurs : en cours, membres sollicités, taux de réponse, disponibles confirmés")
    public ResponseEntity<Statistiques> statistiques() {
        return ResponseEntity.ok(service.statistiques());
    }

    @PostMapping("/api/mobilisations")
    @PreAuthorize(GESTION)
    @Operation(summary = "Étape 1 — Créer une mobilisation (brouillon) avec ses informations générales")
    public ResponseEntity<Detail> creer(@Valid @RequestBody Informations dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @GetMapping("/api/mobilisations/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Détail d'une mobilisation : informations, critères, réponses, objectif, réponses par jour")
    public ResponseEntity<Detail> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PutMapping("/api/mobilisations/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier les informations générales")
    public ResponseEntity<Detail> modifier(@PathVariable Long id, @Valid @RequestBody Informations dto) {
        return ResponseEntity.ok(service.modifierInformations(id, dto));
    }

    @PutMapping("/api/mobilisations/{id}/criteres")
    @PreAuthorize(GESTION)
    @Operation(summary = "Étape 2 — Définir les critères de sélection")
    public ResponseEntity<Detail> criteres(@PathVariable Long id, @Valid @RequestBody Criteres dto) {
        return ResponseEntity.ok(service.definirCriteres(id, dto));
    }

    @GetMapping("/api/mobilisations/{id}/apercu")
    @PreAuthorize(GESTION)
    @Operation(summary = "Membres correspondant aux critères (résultat en temps réel, répartition par région, % vérifiés)")
    public ResponseEntity<Apercu> apercu(@PathVariable Long id, @RequestParam(defaultValue = "50") int limite) {
        return ResponseEntity.ok(service.apercu(id, limite));
    }

    @PostMapping("/api/mobilisations/{id}/envoyer")
    @PreAuthorize(GESTION)
    @Operation(summary = "Étape 3 — Envoyer (ou programmer) la sollicitation aux membres sélectionnés")
    public ResponseEntity<Detail> envoyer(@PathVariable Long id, @Valid @RequestBody Envoi dto) {
        return ResponseEntity.ok(service.envoyer(id, dto));
    }

    @GetMapping("/api/mobilisations/{id}/participants")
    @PreAuthorize(GESTION)
    @Operation(summary = "Réponses des membres (filtre : DISPONIBLE, INDISPONIBLE, EN_ATTENTE)")
    public ResponseEntity<Page<Participant>> participants(@PathVariable Long id,
                                                          @RequestParam(required = false) ReponseMobilisation reponse,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.participants(id, reponse, pageable));
    }

    @GetMapping("/api/mobilisations/{id}/participants/export")
    @PreAuthorize(GESTION)
    @Operation(summary = "Exporter la liste des participants (XLSX, CSV ou PDF)")
    public ResponseEntity<byte[]> exporter(@PathVariable Long id,
                                           @RequestParam(required = false) ReponseMobilisation reponse,
                                           @RequestParam(defaultValue = "XLSX") FormatFichier format) {
        return Telechargement.export(service.exporterParticipants(id, reponse, format), "mobilisation_" + id, format);
    }

    @PostMapping("/api/mobilisations/{id}/relancer")
    @PreAuthorize(GESTION)
    @Operation(summary = "Relancer les membres sans réponse")
    public ResponseEntity<Detail> relancer(@PathVariable Long id) {
        return ResponseEntity.ok(service.relancer(id));
    }

    @PostMapping("/api/mobilisations/{id}/cloturer")
    @PreAuthorize(GESTION)
    @Operation(summary = "Clôturer la mobilisation (remerciements, ajout au parcours SNJ des participants)")
    public ResponseEntity<Detail> cloturer(@PathVariable Long id, @RequestBody(required = false) Cloture dto) {
        return ResponseEntity.ok(service.cloturer(id, dto != null ? dto : new Cloture()));
    }

    @DeleteMapping("/api/mobilisations/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer un brouillon")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/mobilisations")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes mobilisations (sollicitations reçues et ma réponse)")
    public ResponseEntity<List<MaMobilisation>> mesMobilisations() {
        return ResponseEntity.ok(service.mesMobilisations());
    }

    @GetMapping("/api/mon-espace/mobilisations/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Détail d'une mobilisation (profil recherché, « pourquoi vous ? », places confirmées)")
    public ResponseEntity<MaMobilisation> maMobilisation(@PathVariable Long id) {
        return ResponseEntity.ok(service.maMobilisation(id));
    }

    @PostMapping("/api/mon-espace/mobilisations/{id}/reponse")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Confirmer ma disponibilité ou indiquer que je suis indisponible (avec motif)")
    public ResponseEntity<MaMobilisation> repondre(@PathVariable Long id, @Valid @RequestBody ReponseMembre dto) {
        return ResponseEntity.ok(service.repondre(id, dto));
    }
}
