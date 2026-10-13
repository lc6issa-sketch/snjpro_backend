package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DocumentDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import com.example.snjpro_backend.serviceInterface.DocumentServiceInterface;
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
@Tag(name = "15. Documents", description = "Bibliothèque de documents du SNJ (F27), documents personnels des membres et attestations")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')";
    private static final String LECTURE = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";
    private static final String DOSSIER_MEMBRE =
            "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#membreId)";

    private final DocumentServiceInterface service;

    // ---------------------------------------------------------------- Dossiers

    @GetMapping("/api/documents/dossiers")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Dossiers et nombre de documents")
    public ResponseEntity<List<DossierDTO>> dossiers() {
        return ResponseEntity.ok(service.dossiers());
    }

    @PostMapping("/api/documents/dossiers")
    @PreAuthorize(GESTION)
    @Operation(summary = "Créer un dossier")
    public ResponseEntity<DossierDTO> creerDossier(@Valid @RequestBody DossierEdition dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerDossier(dto));
    }

    @PutMapping("/api/documents/dossiers/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Renommer un dossier / changer sa visibilité par défaut")
    public ResponseEntity<DossierDTO> modifierDossier(@PathVariable Long id, @Valid @RequestBody DossierEdition dto) {
        return ResponseEntity.ok(service.modifierDossier(id, dto));
    }

    @DeleteMapping("/api/documents/dossiers/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer un dossier vide")
    public ResponseEntity<Void> supprimerDossier(@PathVariable Long id) {
        service.supprimerDossier(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Documents du SNJ

    @GetMapping("/api/documents")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Lister les documents (filtres : dossier, visibilité MEMBRES / INTERNE / PUBLIC, recherche)")
    public ResponseEntity<List<DocumentDTO>> lister(@RequestParam(required = false) Long dossierId,
                                                    @RequestParam(required = false) VisibiliteDocument visibilite,
                                                    @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(dossierId, visibilite, recherche));
    }

    @GetMapping("/api/documents/espace")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Espace de stockage utilisé")
    public ResponseEntity<Espace> espace() {
        return ResponseEntity.ok(service.espace());
    }

    @PostMapping(value = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GESTION)
    @Operation(summary = "Téléverser un document (PDF, Word, Excel ou image, 20 Mo maximum)")
    public ResponseEntity<DocumentDTO> televerser(@RequestParam("fichier") MultipartFile fichier,
                                                  @RequestParam(required = false) String titre,
                                                  @RequestParam(required = false) Long dossierId,
                                                  @RequestParam(required = false) VisibiliteDocument visibilite) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.televerser(fichier, titre, dossierId, visibilite));
    }

    @PutMapping("/api/documents/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier le titre, le dossier ou la visibilité d'un document")
    public ResponseEntity<DocumentDTO> modifier(@PathVariable Long id, @Valid @RequestBody DocumentEdition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @DeleteMapping("/api/documents/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer un document")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/documents/{id}/telecharger")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Télécharger un document")
    public ResponseEntity<byte[]> telecharger(@PathVariable Long id,
                                              @RequestParam(defaultValue = "false") boolean apercu) {
        Fichier f = service.telecharger(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), apercu);
    }

    @PatchMapping("/api/documents/{id}/verification")
    @PreAuthorize(GESTION)
    @Operation(summary = "Marquer une pièce de membre comme vérifiée (ou non)")
    public ResponseEntity<DocumentDTO> verifier(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean verifie) {
        return ResponseEntity.ok(service.verifier(id, verifie));
    }

    // ---------------------------------------------------------------- Documents d'un membre (onglet « Documents » du profil)

    @GetMapping("/api/membres/{membreId}/documents")
    @PreAuthorize(DOSSIER_MEMBRE)
    @Operation(summary = "Documents d'un membre et pièces obligatoires manquantes")
    public ResponseEntity<DocumentsMembre> documentsMembre(@PathVariable Long membreId) {
        return ResponseEntity.ok(service.documentsMembre(membreId));
    }

    @PostMapping(value = "/api/membres/{membreId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(DOSSIER_MEMBRE)
    @Operation(summary = "Téléverser une pièce pour un membre (identité, photo, diplôme…)")
    public ResponseEntity<DocumentDTO> televerserPourMembre(@PathVariable Long membreId,
                                                            @RequestParam("fichier") MultipartFile fichier,
                                                            @RequestParam(defaultValue = "AUTRE") TypeDocument type,
                                                            @RequestParam(required = false) String titre) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.televerserPourMembre(membreId, fichier, type, titre));
    }

    @PostMapping("/api/membres/{membreId}/documents/attestation")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Générer l'attestation de participation (PDF) — le membre est notifié")
    public ResponseEntity<DocumentDTO> attestation(@PathVariable Long membreId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.genererAttestation(membreId));
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/documents")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes documents : personnels et documents du SNJ")
    public ResponseEntity<MesDocuments> mesDocuments() {
        return ResponseEntity.ok(service.mesDocuments());
    }

    @PostMapping(value = "/api/mon-espace/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Ajouter un de mes documents (photo, pièce d'identité, diplôme…)")
    public ResponseEntity<DocumentDTO> televerserMonDocument(@RequestParam("fichier") MultipartFile fichier,
                                                             @RequestParam(defaultValue = "AUTRE") TypeDocument type,
                                                             @RequestParam(required = false) String titre) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.televerserMonDocument(fichier, type, titre));
    }

    @GetMapping("/api/mon-espace/documents/{id}/telecharger")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Télécharger un de mes documents ou un document du SNJ")
    public ResponseEntity<byte[]> telechargerMonDocument(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "false") boolean apercu) {
        Fichier f = service.telechargerMonDocument(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), apercu);
    }
}
