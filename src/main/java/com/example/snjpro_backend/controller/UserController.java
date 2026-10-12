package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.serviceImpl.RoleService;
import com.example.snjpro_backend.serviceInterface.AuthAvanceServiceInterface;
import com.example.snjpro_backend.serviceInterface.UserServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "02. Utilisateurs & rôles", description = "Gestion des comptes administratifs et de leurs rôles")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserServiceInterface userService;
    private final AuthAvanceServiceInterface authAvance;
    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Créer un utilisateur",
            description = "region est obligatoire pour ROLE_RESPONSABLE_REGIONAL, cohorteId pour ROLE_RESPONSABLE_COHORTE.")
    public ResponseEntity<UserResponseDTO> creer(@Valid @RequestBody UserDTO dto, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.creerUtilisateur(dto, auth.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Lister les utilisateurs (filtrables par rôle et statut)")
    public ResponseEntity<List<UserResponseDTO>> listerTous(@RequestParam(required = false) RoleEnum role,
                                                            @RequestParam(required = false) Boolean actif,
                                                            @RequestParam(required = false) String recherche) {
        String q = recherche == null ? null : recherche.trim().toLowerCase();
        return ResponseEntity.ok(userService.obtenirTousLesUtilisateurs(role, actif).stream()
                .filter(u -> q == null || q.isEmpty() || (u.getUsername() + " " + u.getEmail() + " " + u.getPrenom() + " " + u.getNom())
                        .toLowerCase().contains(q))
                .toList());
    }

    @PostMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Inviter un utilisateur : un e-mail d'activation lui est envoyé, il choisit son mot de passe")
    public ResponseEntity<UserResponseDTO> inviter(@Valid @RequestBody InvitationDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authAvance.inviter(dto));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Invitations en attente")
    public ResponseEntity<List<UserResponseDTO>> invitations() {
        return ResponseEntity.ok(authAvance.invitationsEnAttente());
    }

    @PostMapping("/invitations/{id}/renvoyer")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Renvoyer l'e-mail d'invitation")
    public ResponseEntity<UserResponseDTO> renvoyerInvitation(@PathVariable Long id) {
        return ResponseEntity.ok(authAvance.renvoyerInvitation(id));
    }

    @GetMapping("/roles/detail")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Rôles : libellé, description, périmètre, nombre d'utilisateurs et permissions")
    public ResponseEntity<List<RoleDTOs.Role>> rolesDetail() {
        return ResponseEntity.ok(roleService.roles());
    }

    @GetMapping("/roles/{role}/permissions")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Permissions d'un rôle (voir / créer / modifier / supprimer par module)")
    public ResponseEntity<RoleDTOs.Role> permissions(@PathVariable RoleEnum role) {
        return ResponseEntity.ok(roleService.role(role));
    }

    @PutMapping("/roles/{role}/permissions")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Enregistrer les permissions d'un rôle")
    public ResponseEntity<RoleDTOs.Role> enregistrerPermissions(@PathVariable RoleEnum role, @RequestBody RoleDTOs.Permissions dto) {
        return ResponseEntity.ok(roleService.enregistrer(role, dto.getPermissions()));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Lister les rôles disponibles")
    public ResponseEntity<List<RoleEnum>> roles() {
        return ResponseEntity.ok(Arrays.asList(RoleEnum.values()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ')")
    @Operation(summary = "Obtenir un utilisateur par ID")
    public ResponseEntity<UserResponseDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(userService.obtenirUtilisateur(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Modifier un utilisateur (mot de passe facultatif)")
    public ResponseEntity<UserResponseDTO> modifier(@PathVariable Long id, @Valid @RequestBody UserDTO dto,
                                                    Authentication auth) {
        return ResponseEntity.ok(userService.modifierUtilisateur(id, dto, auth.getName()));
    }

    @PatchMapping("/{id}/actif")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Activer / désactiver un compte")
    public ResponseEntity<UserResponseDTO> activerDesactiver(@PathVariable Long id, @RequestParam boolean actif,
                                                             Authentication auth) {
        return ResponseEntity.ok(userService.activerDesactiverUtilisateur(id, actif, auth.getName()));
    }

    @PatchMapping("/{id}/mot-de-passe")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Réinitialiser le mot de passe d'un utilisateur")
    public ResponseEntity<UserResponseDTO> reinitialiserMotDePasse(@PathVariable Long id,
                                                                   @Valid @RequestBody NouveauMotDePasseDTO dto) {
        return ResponseEntity.ok(userService.reinitialiserMotDePasse(id, dto.getNouveauMotDePasse()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @Operation(summary = "Supprimer un utilisateur")
    public ResponseEntity<Void> supprimer(@PathVariable Long id, Authentication auth) {
        userService.supprimerUtilisateur(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
