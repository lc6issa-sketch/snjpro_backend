package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DemandeDTOs.*;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.enumeration.Enumerations.StatutDemande;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDemande;
import com.example.snjpro_backend.serviceInterface.DemandeServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "16. Demandes des membres", description = "Demandes et réclamations (F29) : dépôt, suivi et traitement")
@SecurityRequirement(name = "bearerAuth")
public class DemandeController {
    private static final String TRAITEMENT = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final DemandeServiceInterface service;

    @GetMapping("/api/demandes")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Lister les demandes de mon périmètre (filtres : statut, type, recherche nom / objet / numéro)")
    public ResponseEntity<List<Resume>> lister(@RequestParam(required = false) StatutDemande statut,
                                               @RequestParam(required = false) TypeDemande type,
                                               @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(statut, type, recherche));
    }

    @GetMapping("/api/demandes/statistiques")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Demandes en attente, en cours, délai moyen de traitement")
    public ResponseEntity<Statistiques> statistiques() {
        return ResponseEntity.ok(service.statistiques());
    }

    @GetMapping("/api/demandes/{id}")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Détail d'une demande (message, modifications demandées, pièce jointe, suivi)")
    public ResponseEntity<Detail> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping("/api/demandes/{id}/prise-en-charge")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Prendre en charge une demande")
    public ResponseEntity<Detail> prendreEnCharge(@PathVariable Long id) {
        return ResponseEntity.ok(service.prendreEnCharge(id));
    }

    @PostMapping("/api/demandes/{id}/valider")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Valider la demande (applique la modification au profil si demandé ; le membre est notifié)")
    public ResponseEntity<Detail> valider(@PathVariable Long id, @Valid @RequestBody Validation dto) {
        return ResponseEntity.ok(service.valider(id, dto));
    }

    @PostMapping("/api/demandes/{id}/rejeter")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Rejeter la demande avec un motif")
    public ResponseEntity<Detail> rejeter(@PathVariable Long id, @Valid @RequestBody Rejet dto) {
        return ResponseEntity.ok(service.rejeter(id, dto));
    }

    @PostMapping("/api/demandes/{id}/complement")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Demander un complément au membre (éléments à fournir, délai)")
    public ResponseEntity<Detail> complement(@PathVariable Long id, @Valid @RequestBody Complement dto) {
        return ResponseEntity.ok(service.demanderComplement(id, dto));
    }

    @GetMapping("/api/demandes/{id}/piece-jointe")
    @PreAuthorize(TRAITEMENT)
    @Operation(summary = "Télécharger la pièce jointe")
    public ResponseEntity<byte[]> pieceJointe(@PathVariable Long id) {
        Fichier f = service.pieceJointe(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/demandes")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes demandes (traitees = false : en cours ; true : traitées ; vide : toutes)")
    public ResponseEntity<List<Resume>> mesDemandes(@RequestParam(required = false) Boolean traitees) {
        return ResponseEntity.ok(service.mesDemandes(traitees));
    }

    @GetMapping("/api/mon-espace/demandes/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Détail et suivi d'une de mes demandes")
    public ResponseEntity<Detail> maDemande(@PathVariable Long id) {
        return ResponseEntity.ok(service.maDemande(id));
    }

    @PostMapping(value = "/api/mon-espace/demandes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Envoyer une demande (partie « demande » en JSON + pièce jointe facultative)",
            description = "Le numéro de suivi est attribué automatiquement (ex. DEM-2026-0421).")
    public ResponseEntity<Detail> creer(@Valid @RequestPart("demande") Nouvelle demande,
                                        @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(demande, fichier));
    }

    @PostMapping(value = "/api/mon-espace/demandes/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Envoyer une demande sans pièce jointe (JSON simple)")
    public ResponseEntity<Detail> creerJson(@Valid @RequestBody Nouvelle demande) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(demande, null));
    }

    @PostMapping(value = "/api/mon-espace/demandes/{id}/complement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Répondre à une demande de complément (message et/ou pièce)")
    public ResponseEntity<Detail> completer(@PathVariable Long id,
                                            @RequestParam(required = false) String message,
                                            @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        return ResponseEntity.ok(service.completer(id, message, fichier));
    }

    @DeleteMapping("/api/mon-espace/demandes/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Annuler une demande pas encore prise en charge")
    public ResponseEntity<Void> annuler(@PathVariable Long id) {
        service.annuler(id);
        return ResponseEntity.noContent().build();
    }
}
