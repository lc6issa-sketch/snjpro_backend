package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.EspaceMembreDTOs.*;
import com.example.snjpro_backend.serviceInterface.EspaceMembreServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Accueil de l'application mobile, carte numérique et CV (côté membre et côté gestionnaires). */
@RestController
@RequiredArgsConstructor
@Tag(name = "06. Mon profil (membre)")
@SecurityRequirement(name = "bearerAuth")
public class EspaceMembreController {

    private static final String GESTIONNAIRES = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final EspaceMembreServiceInterface service;

    @GetMapping("/api/mon-espace/accueil")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Écran d'accueil mobile : profil, complétude, mobilisation en cours, actualités, compteurs non lus")
    public ResponseEntity<Accueil> accueil() {
        return ResponseEntity.ok(service.accueil());
    }

    @GetMapping("/api/membres/{id}/carte")
    @PreAuthorize(GESTIONNAIRES + " and @perimetre.peutAcceder(#id)")
    @Operation(summary = "Carte de membre numérique d'un membre")
    public ResponseEntity<Carte> carte(@PathVariable Long id) {
        return ResponseEntity.ok(service.carte(id));
    }

    @PostMapping("/api/membres/carte/verification")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'DIRECTION', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')")
    @Operation(summary = "Vérifier l'authenticité d'une carte à partir du contenu scanné de son QR code")
    public ResponseEntity<VerificationCarte> verifierCarte(@RequestBody java.util.Map<String, String> corps) {
        return ResponseEntity.ok(service.verifierCarte(corps.get("qr")));
    }

    @PostMapping("/api/cartes/scan")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Scanner la carte d'un membre : profil public (parcours, compétences) et possibilité de lui écrire")
    public ResponseEntity<ProfilScanne> scannerCarte(@RequestBody java.util.Map<String, String> corps) {
        return ResponseEntity.ok(service.profilScanne(corps.get("qr")));
    }

    @GetMapping("/api/cartes/{identifiantSnj}/{signature}/photo")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Photo du membre dont la carte a été scannée (la signature du QR sert de preuve)")
    public ResponseEntity<byte[]> photoCarteScannee(@PathVariable String identifiantSnj, @PathVariable String signature) {
        var f = service.photoScannee(identifiantSnj, signature);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), true);
    }

    @GetMapping("/api/membres/{id}/completude")
    @PreAuthorize(GESTIONNAIRES + " and @perimetre.peutAcceder(#id)")
    @Operation(summary = "Complétude du profil d'un membre et éléments manquants")
    public ResponseEntity<Completude> completude(@PathVariable Long id) {
        return ResponseEntity.ok(service.completude(id));
    }

    @GetMapping("/api/membres/{id}/cv")
    @PreAuthorize(GESTIONNAIRES + " and @perimetre.peutAcceder(#id)")
    @Operation(summary = "CV numérique d'un membre (données)")
    public ResponseEntity<Cv> cv(@PathVariable Long id) {
        return ResponseEntity.ok(service.cv(id));
    }

    @GetMapping("/api/membres/{id}/cv/pdf")
    @PreAuthorize(GESTIONNAIRES + " and @perimetre.peutAcceder(#id)")
    @Operation(summary = "CV numérique d'un membre (PDF)")
    public ResponseEntity<byte[]> cvPdf(@PathVariable Long id) {
        return Telechargement.fichier(service.cvPdf(id), "cv_membre_" + id + ".pdf", "application/pdf", false);
    }
}
