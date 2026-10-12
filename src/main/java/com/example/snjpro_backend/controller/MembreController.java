package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.CompteMembreDTO;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.MembreDTO;
import com.example.snjpro_backend.dto.MembreResponseDTO;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/membres")
@RequiredArgsConstructor
@Tag(name = "04. Membres", description = "Gestion des membres SNJ (ajout, modification, consultation, archivage, identifiant unique)")
@SecurityRequirement(name = "bearerAuth")
public class MembreController {

    private static final String GESTIONNAIRES = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final MembreServiceInterface membreService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_COHORTE')")
    @Operation(summary = "Ajouter un membre",
            description = "Attribue automatiquement un identifiant unique SNJ. Refuse les doublons évidents "
                    + "(téléphone, email, ou nom + prénom + date de naissance) sauf si forcer=true.")
    public ResponseEntity<MembreResponseDTO> ajouter(@Valid @RequestBody MembreDTO dto,
                                                     @RequestParam(defaultValue = "false") boolean forcer,
                                                     Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membreService.ajouterMembre(dto, auth.getName(), forcer));
    }

    @GetMapping
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Lister les membres (paginé, limité au périmètre de l'utilisateur)")
    public ResponseEntity<Page<MembreResponseDTO>> listerTous(
            @ParameterObject @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        return ResponseEntity.ok(membreService.obtenirTousLesMembres(pageable));
    }

    @GetMapping("/doublons/verifier")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Vérifier les doublons potentiels avant d'enregistrer (formulaire « Nouveau membre »)")
    public ResponseEntity<Map<String, Object>> verifierDoublons(@RequestParam(required = false) String prenom,
                                                                @RequestParam(required = false) String nom,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
                                                                @RequestParam(required = false) String telephone,
                                                                @RequestParam(required = false) String email,
                                                                @RequestParam(required = false) Long exclureId) {
        List<String> motifs = membreService.verifierDoublons(prenom, nom, dateNaissance, telephone, email, exclureId);
        return ResponseEntity.ok(Map.of("doublonPotentiel", !motifs.isEmpty(), "motifs", motifs));
    }

    @GetMapping("/identifiant-suivant")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Identifiant SNJ qui sera attribué au prochain membre de la cohorte")
    public ResponseEntity<Map<String, String>> identifiantSuivant(@RequestParam Long cohorteId) {
        return ResponseEntity.ok(Map.of("identifiantSnj", membreService.identifiantSuivant(cohorteId)));
    }

    @PatchMapping("/{id}/disponibilite")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#id)")
    @Operation(summary = "Disponible (ou non) pour les mobilisations")
    public ResponseEntity<MembreResponseDTO> disponibilite(@PathVariable Long id, @RequestParam boolean disponible) {
        return ResponseEntity.ok(membreService.changerDisponibilite(id, disponible));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#id)")
    @Operation(summary = "Téléverser la photo d'identité d'un membre")
    public ResponseEntity<MembreResponseDTO> changerPhoto(@PathVariable Long id, @RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.ok(membreService.changerPhoto(id, fichier));
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("@perimetre.peutAcceder(#id)")
    @Operation(summary = "Photo d'identité d'un membre")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        Fichier f = membreService.photo(id);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), true);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perimetre.peutAcceder(#id)")
    @Operation(summary = "Consulter un membre")
    public ResponseEntity<MembreResponseDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(membreService.obtenirMembre(id));
    }

    @GetMapping("/snj/{identifiantSnj}")
    @PreAuthorize("@perimetre.peutAccederParIdentifiant(#identifiantSnj)")
    @Operation(summary = "Consulter un membre par son identifiant SNJ")
    public ResponseEntity<MembreResponseDTO> obtenirParIdentifiant(@PathVariable String identifiantSnj) {
        return ResponseEntity.ok(membreService.obtenirParIdentifiantSnj(identifiantSnj));
    }

    @GetMapping("/recherche")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Recherche rapide par nom, prénom ou identifiant")
    public ResponseEntity<Page<MembreResponseDTO>> rechercher(
            @Parameter(description = "Texte recherché") @RequestParam String q,
            @ParameterObject @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        return ResponseEntity.ok(membreService.rechercherMembres(q, pageable));
    }

    @GetMapping("/cohorte/{cohorteId}")
    @PreAuthorize(GESTIONNAIRES)
    @Operation(summary = "Lister les membres d'une cohorte")
    public ResponseEntity<Page<MembreResponseDTO>> parCohorte(
            @PathVariable Long cohorteId,
            @ParameterObject @PageableDefault(size = 20, sort = "nom") Pageable pageable) {
        return ResponseEntity.ok(membreService.obtenirMembresParCohorte(cohorteId, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_COHORTE') and @perimetre.peutAcceder(#id)")
    @Operation(summary = "Modifier un membre")
    public ResponseEntity<MembreResponseDTO> modifier(@PathVariable Long id, @Valid @RequestBody MembreDTO dto) {
        return ResponseEntity.ok(membreService.modifierMembre(id, dto));
    }

    @PatchMapping("/{id}/archiver")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Archiver un membre")
    public ResponseEntity<MembreResponseDTO> archiver(@PathVariable Long id) {
        return ResponseEntity.ok(membreService.archiverMembre(id));
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Changer le statut d'un membre (ACTIF, INACTIF, ARCHIVE, DECEDE, SUSPENDU)")
    public ResponseEntity<MembreResponseDTO> changerStatut(@PathVariable Long id, @RequestParam StatutMembreEnum statut) {
        return ResponseEntity.ok(membreService.changerStatut(id, statut));
    }

    @PostMapping("/{id}/compte")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Créer le compte de connexion (rôle MEMBRE) d'un membre",
            description = "Nom d'utilisateur par défaut : l'identifiant SNJ en minuscules. Permet au membre "
                    + "d'accéder à /api/mon-profil (application mobile).")
    public ResponseEntity<MembreResponseDTO> creerCompte(@PathVariable Long id, @Valid @RequestBody CompteMembreDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membreService.creerCompte(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Supprimer définitivement un membre")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        membreService.supprimerMembre(id);
        return ResponseEntity.noContent().build();
    }
}
