package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.SouvenirDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutSouvenir;
import com.example.snjpro_backend.enumeration.Enumerations.TypeMedia;
import com.example.snjpro_backend.serviceInterface.SouvenirServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "20. Souvenirs de formation", description = "Photos, vidéos et sons de la période de formation, publiés par le personnel ou envoyés par les membres puis modérés")
@SecurityRequirement(name = "bearerAuth")
public class SouvenirController {
    private static final String LECTURE = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";
    private static final String GESTION = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final SouvenirServiceInterface service;

    @GetMapping("/api/souvenirs")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Lister les souvenirs du périmètre (filtres : cohorte, statut, type de média, recherche)")
    public ResponseEntity<Page<Resume>> lister(@RequestParam(required = false) Long cohorteId,
                                               @RequestParam(required = false) StatutSouvenir statut,
                                               @RequestParam(required = false) TypeMedia type,
                                               @RequestParam(required = false) String recherche,
                                               @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(service.lister(cohorteId, statut, type, recherche, pageable));
    }

    @GetMapping("/api/souvenirs/statistiques")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Compteurs : publiés, à valider, rejetés, nombre de photos / vidéos / sons publiés")
    public ResponseEntity<Statistiques> statistiques(@RequestParam(required = false) Long cohorteId) {
        return ResponseEntity.ok(service.statistiques(cohorteId));
    }

    @GetMapping("/api/souvenirs/{id}")
    @PreAuthorize(LECTURE)
    @Operation(summary = "Détail d'un souvenir")
    public ResponseEntity<Resume> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping(value = "/api/souvenirs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GESTION)
    @Operation(summary = "Publier un souvenir (champs de formulaire + fichiers « fichiers », 10 au plus) — en ligne immédiatement")
    public ResponseEntity<Resume> creer(@Valid @ModelAttribute Edition dto,
                                        @RequestParam(value = "fichiers", required = false) List<MultipartFile> fichiers) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto, fichiers));
    }

    @PutMapping("/api/souvenirs/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Modifier le titre, la légende, la cohorte, la date, le lieu ou la visibilité")
    public ResponseEntity<Resume> modifier(@PathVariable Long id, @Valid @RequestBody Edition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PostMapping(value = "/api/souvenirs/{id}/medias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(GESTION)
    @Operation(summary = "Ajouter des fichiers à un souvenir")
    public ResponseEntity<Resume> ajouterMedias(@PathVariable Long id, @RequestParam("fichiers") List<MultipartFile> fichiers) {
        return ResponseEntity.ok(service.ajouterMedias(id, fichiers));
    }

    @DeleteMapping("/api/souvenirs/{id}/medias/{mediaId}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Retirer un fichier d'un souvenir")
    public ResponseEntity<Resume> supprimerMedia(@PathVariable Long id, @PathVariable Long mediaId) {
        return ResponseEntity.ok(service.supprimerMedia(id, mediaId));
    }

    @PostMapping("/api/souvenirs/{id}/valider")
    @PreAuthorize(GESTION)
    @Operation(summary = "Valider (publier) un souvenir envoyé par un membre")
    public ResponseEntity<Resume> valider(@PathVariable Long id) {
        return ResponseEntity.ok(service.valider(id));
    }

    @PostMapping("/api/souvenirs/{id}/rejeter")
    @PreAuthorize(GESTION)
    @Operation(summary = "Rejeter un souvenir (motif obligatoire, visible par le membre)")
    public ResponseEntity<Resume> rejeter(@PathVariable Long id, @Valid @RequestBody Rejet dto) {
        return ResponseEntity.ok(service.rejeter(id, dto.getMotif()));
    }

    @DeleteMapping("/api/souvenirs/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Supprimer un souvenir et ses fichiers")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/souvenirs/medias/{mediaId}/fichier")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Contenu d'une photo, vidéo ou son (gestionnaire du périmètre, ou membre autorisé)")
    public ResponseEntity<byte[]> fichier(@PathVariable Long mediaId) {
        Fichier f = service.fichier(mediaId);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime() != null ? f.typeMime() : "application/octet-stream", true);
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/souvenirs")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Souvenirs publiés de ma cohorte et souvenirs visibles par tous")
    public ResponseEntity<List<Resume>> fil() {
        return ResponseEntity.ok(service.filMembre());
    }

    @GetMapping("/api/mon-espace/souvenirs/mes-envois")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes envois (en attente, publiés ou rejetés avec le motif)")
    public ResponseEntity<List<Resume>> mesEnvois() {
        return ResponseEntity.ok(service.mesEnvois());
    }

    @PostMapping(value = "/api/mon-espace/souvenirs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Envoyer un souvenir (rattaché à ma cohorte, publié après validation)")
    public ResponseEntity<Resume> soumettre(@Valid @ModelAttribute Edition dto,
                                            @RequestParam(value = "fichiers", required = false) List<MultipartFile> fichiers) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.soumettre(dto, fichiers));
    }

    @DeleteMapping("/api/mon-espace/souvenirs/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Supprimer un de mes envois")
    public ResponseEntity<Void> supprimerMonEnvoi(@PathVariable Long id) {
        service.supprimerMonEnvoi(id);
        return ResponseEntity.noContent().build();
    }
}
