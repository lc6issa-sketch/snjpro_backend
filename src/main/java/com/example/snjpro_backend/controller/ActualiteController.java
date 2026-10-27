package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.ActualiteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.CategorieActualite;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import com.example.snjpro_backend.serviceImpl.StockageFichierService;
import com.example.snjpro_backend.serviceInterface.ActualiteServiceInterface;
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
@Tag(name = "13. Actualités", description = "Publication des informations et annonces du SNJ (F23)")
@SecurityRequirement(name = "bearerAuth")
public class ActualiteController {
    // Responsables : actualités limitées à leur région ou à leur cohorte (contrôlé dans le service)
    private static final String REDACTEURS = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final ActualiteServiceInterface service;

    @GetMapping("/api/actualites")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Lister les actualités (filtres : statut, catégorie, recherche)")
    public ResponseEntity<List<Resume>> lister(@RequestParam(required = false) StatutActualite statut,
                                               @RequestParam(required = false) CategorieActualite categorie,
                                               @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.lister(statut, categorie, recherche));
    }

    @GetMapping("/api/actualites/compteurs")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Nombre d'actualités par statut (onglets Toutes / Publiées / Brouillons / Archivées)")
    public ResponseEntity<Compteurs> compteurs() {
        return ResponseEntity.ok(service.compteurs());
    }

    @GetMapping("/api/actualites/{id}")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Obtenir une actualité (éditeur, aperçu)")
    public ResponseEntity<Detail> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PostMapping("/api/actualites")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Créer une actualité (brouillon, programmée ou publiée immédiatement)")
    public ResponseEntity<Detail> creer(@Valid @RequestBody Edition dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PutMapping("/api/actualites/{id}")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Modifier une actualité")
    public ResponseEntity<Detail> modifier(@PathVariable Long id, @Valid @RequestBody Edition dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PostMapping("/api/actualites/{id}/publier")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Publier maintenant (notifie les membres visés si l'option est activée)")
    public ResponseEntity<Detail> publier(@PathVariable Long id) {
        return ResponseEntity.ok(service.publier(id));
    }

    @PostMapping("/api/actualites/{id}/archiver")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Archiver une actualité")
    public ResponseEntity<Detail> archiver(@PathVariable Long id) {
        return ResponseEntity.ok(service.archiver(id));
    }

    @DeleteMapping("/api/actualites/{id}")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Supprimer un brouillon ou une actualité archivée")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/actualites/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Changer l'image de couverture")
    public ResponseEntity<Detail> image(@PathVariable Long id, @RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.ok(service.changerImage(id, fichier));
    }

    @GetMapping("/api/actualites/{id}/image")
    @Operation(summary = "Image de couverture (accessible à tout utilisateur connecté)")
    public ResponseEntity<byte[]> lireImage(@PathVariable Long id) {
        byte[] contenu = service.image(id);
        return Telechargement.fichier(contenu, "actualite-" + id, detecterType(contenu), true);
    }

    @GetMapping("/api/actualites/{id}/inscrits")
    @PreAuthorize(REDACTEURS)
    @Operation(summary = "Membres inscrits à l'événement annoncé")
    public ResponseEntity<List<Inscrit>> inscrits(@PathVariable Long id) {
        return ResponseEntity.ok(service.inscrits(id));
    }

    // ---------------------------------------------------------------- Espace membre

    @GetMapping("/api/mon-espace/actualites")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Fil d'actualités du membre (filtre : catégorie)")
    public ResponseEntity<List<Resume>> fil(@RequestParam(required = false) CategorieActualite categorie) {
        return ResponseEntity.ok(service.fil(categorie));
    }

    @GetMapping("/api/mon-espace/actualites/a-la-une")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Actualité mise « À la une »")
    public ResponseEntity<Resume> aLaUne() {
        Resume r = service.aLaUne();
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/api/mon-espace/actualites/{id}")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Lire une actualité (compte une vue)")
    public ResponseEntity<Detail> lire(@PathVariable Long id) {
        return ResponseEntity.ok(service.lire(id));
    }

    @PostMapping("/api/mon-espace/actualites/{id}/inscription")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "S'inscrire à l'événement annoncé (ex. forum emploi)")
    public ResponseEntity<Detail> inscrire(@PathVariable Long id) {
        return ResponseEntity.ok(service.inscrire(id));
    }

    @DeleteMapping("/api/mon-espace/actualites/{id}/inscription")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Annuler mon inscription")
    public ResponseEntity<Detail> desinscrire(@PathVariable Long id) {
        return ResponseEntity.ok(service.desinscrire(id));
    }

    static String detecterType(byte[] c) {
        if (c.length > 3 && (c[0] & 0xFF) == 0x89 && c[1] == 'P') return "image/png";
        if (c.length > 2 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (c.length > 3 && c[0] == 'G' && c[1] == 'I') return "image/gif";
        if (c.length > 11 && c[8] == 'W' && c[9] == 'E') return "image/webp";
        return StockageFichierService.typeMime("x.bin");
    }
}
