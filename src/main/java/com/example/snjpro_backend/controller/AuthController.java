package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.dto.AuthAvanceDTOs.*;
import com.example.snjpro_backend.dto.AuthResponse;
import com.example.snjpro_backend.dto.ChangementMotDePasseDTO;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.LoginRequest;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.exception.TropDeTentativesException;
import com.example.snjpro_backend.security.JetonRevocationService;
import com.example.snjpro_backend.security.JwtAuthenticationFilter;
import com.example.snjpro_backend.security.JwtUtils;
import com.example.snjpro_backend.security.LoginAttemptService;
import com.example.snjpro_backend.serviceInterface.AuthAvanceServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.UserServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "01. Authentification", description = "Connexion (mot de passe, téléphone, code SMS), activation, mot de passe oublié, "
        + "invitations, sessions et paramètres du compte")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final MembreRepository membreRepository;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final JetonRevocationService jetonRevocationService;
    private final UserServiceInterface userService;
    private final AuthAvanceServiceInterface authAvance;
    private final HistoriqueServiceInterface historiqueService;
    private final com.example.snjpro_backend.serviceImpl.RoleService roleService;

    // ================================================================= Connexion

    @PostMapping("/login")
    @Operation(summary = "Connexion (nom d'utilisateur ou e-mail + mot de passe)",
            description = "Retourne un token JWT. Si la double authentification est activée, deuxiemeFacteurRequis = true : "
                    + "le code reçu par SMS se valide via POST /api/auth/login/code/verifier. "
                    + "Après 5 échecs consécutifs, le compte est bloqué 15 minutes.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest requete) {
        String username = loginRequest.getUsername().trim();
        if (loginAttemptService.estBloque(username)) {
            throw new TropDeTentativesException("Trop de tentatives échouées. Réessayez dans "
                    + loginAttemptService.minutesRestantes(username) + " minute(s).");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifiantConnexion(username), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            loginAttemptService.echec(username);
            historiqueService.enregistrer("CONNEXION_ECHOUEE", "UTILISATEUR", null, "Identifiant : " + username, username);
            throw e;
        }
        loginAttemptService.succes(username);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return ResponseEntity.ok(authAvance.apresAuthentification((User) authentication.getPrincipal(), requete));
    }

    @PostMapping("/mobile/login")
    @Operation(summary = "Connexion depuis l'application mobile (numéro de téléphone + mot de passe)")
    public ResponseEntity<AuthResponse> loginTelephone(@Valid @RequestBody ConnexionTelephone dto, HttpServletRequest requete) {
        return ResponseEntity.ok(authAvance.connexionTelephone(dto, requete));
    }

    @PostMapping("/login/code/demander")
    @Operation(summary = "Recevoir un code de connexion par SMS (identifiant : nom d'utilisateur, e-mail ou téléphone)")
    public ResponseEntity<Map<String, Object>> demanderCode(@Valid @RequestBody DemandeCode dto) {
        return ResponseEntity.ok(authAvance.demanderCodeConnexion(dto));
    }

    @PostMapping("/login/code/verifier")
    @Operation(summary = "Se connecter avec le code reçu par SMS (connexion par code ou double authentification)")
    public ResponseEntity<AuthResponse> verifierCode(@Valid @RequestBody VerificationCodeConnexion dto, HttpServletRequest requete) {
        return ResponseEntity.ok(authAvance.verifierCodeConnexion(dto, requete));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Déconnexion", description = "Invalide le token JWT courant : il ne pourra plus être utilisé.")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, Authentication authentication) {
        String jwt = JwtAuthenticationFilter.parseJwt(request);
        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
            jetonRevocationService.revoquer(jwt);
            authAvance.fermerSession(jwt);
        }
        if (authentication != null) {
            historiqueService.enregistrer("DECONNEXION", "UTILISATEUR", null, null, authentication.getName());
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie."));
    }

    // ================================================================= Mot de passe oublié

    @PostMapping("/mot-de-passe-oublie")
    @Operation(summary = "Mot de passe oublié (web) : lien de réinitialisation envoyé par e-mail, valable 1 heure")
    public ResponseEntity<Map<String, Object>> motDePasseOublie(@Valid @RequestBody Email dto) {
        return ResponseEntity.ok(authAvance.motDePasseOublieEmail(dto));
    }

    @PostMapping("/mot-de-passe-oublie/sms")
    @Operation(summary = "Mot de passe oublié (mobile) : code envoyé par SMS")
    public ResponseEntity<Map<String, Object>> motDePasseOublieSms(@Valid @RequestBody Telephone dto) {
        return ResponseEntity.ok(authAvance.motDePasseOublieSms(dto));
    }

    @PostMapping("/mot-de-passe-oublie/verifier")
    @Operation(summary = "Vérifier le code SMS de réinitialisation : renvoie un jeton pour choisir le nouveau mot de passe")
    public ResponseEntity<JetonEtape> verifierReinitialisation(@Valid @RequestBody VerificationTelephone dto) {
        return ResponseEntity.ok(authAvance.verifierCodeReinitialisation(dto));
    }

    @PostMapping("/reinitialiser")
    @Operation(summary = "Choisir un nouveau mot de passe (jeton du lien e-mail ou jeton obtenu après le code SMS)")
    public ResponseEntity<Map<String, String>> reinitialiser(@Valid @RequestBody Reinitialisation dto) {
        return ResponseEntity.ok(authAvance.reinitialiser(dto));
    }

    // ================================================================= Activation du compte membre

    @PostMapping("/activation/demander")
    @Operation(summary = "Première connexion d'un membre : code d'activation envoyé au numéro enregistré au SNJ")
    public ResponseEntity<Map<String, Object>> demanderActivation(@Valid @RequestBody Telephone dto) {
        return ResponseEntity.ok(authAvance.demanderActivation(dto));
    }

    @PostMapping("/activation/verifier")
    @Operation(summary = "Vérifier le code d'activation : renvoie un jeton pour créer le mot de passe")
    public ResponseEntity<JetonEtape> verifierActivation(@Valid @RequestBody VerificationTelephone dto) {
        return ResponseEntity.ok(authAvance.verifierActivation(dto));
    }

    @PostMapping("/activation/finaliser")
    @Operation(summary = "Créer le mot de passe : le compte membre est créé et la personne est connectée")
    public ResponseEntity<AuthResponse> finaliserActivation(@Valid @RequestBody Activation dto, HttpServletRequest requete) {
        return ResponseEntity.ok(authAvance.finaliserActivation(dto, requete));
    }

    // ================================================================= Invitation

    @GetMapping("/invitation")
    @Operation(summary = "Informations d'une invitation (écran d'acceptation)")
    public ResponseEntity<InfoInvitation> infoInvitation(@RequestParam String jeton) {
        return ResponseEntity.ok(authAvance.infoInvitation(jeton));
    }

    @PostMapping("/invitation/accepter")
    @Operation(summary = "Accepter une invitation en choisissant son mot de passe")
    public ResponseEntity<AuthResponse> accepterInvitation(@Valid @RequestBody AcceptationInvitation dto, HttpServletRequest requete) {
        return ResponseEntity.ok(authAvance.accepterInvitation(dto, requete));
    }

    // ================================================================= Mon compte (paramètres)

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Utilisateur courant")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        AuthResponse r = AuthResponse.of(null, null, user);
        membreRepository.findByUserId(user.getId()).ifPresent(m -> r.setMembreId(m.getId()));   // comme à la connexion
        return ResponseEntity.ok(r);
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Modifier mon profil (prénom, nom, e-mail, téléphone) — le rôle et le périmètre ne sont pas modifiables")
    public ResponseEntity<AuthResponse> modifierMonCompte(@Valid @RequestBody MonCompte dto) {
        return ResponseEntity.ok(authAvance.modifierMonCompte(dto));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Changer ma photo de profil")
    public ResponseEntity<AuthResponse> changerPhoto(@RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.ok(authAvance.changerPhoto(fichier));
    }

    @GetMapping("/me/photo")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ma photo de profil")
    public ResponseEntity<byte[]> maPhoto() {
        Fichier f = authAvance.maPhoto();
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), true);
    }

    @GetMapping("/utilisateurs/{userId}/photo")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Photo de profil d'un utilisateur (avatars de la messagerie, de la liste des utilisateurs…)")
    public ResponseEntity<byte[]> photoUtilisateur(@PathVariable Long userId) {
        Fichier f = authAvance.photoUtilisateur(userId);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), true);
    }

    @PutMapping("/mot-de-passe")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Changer son mot de passe")
    public ResponseEntity<Map<String, String>> changerMotDePasse(@Valid @RequestBody ChangementMotDePasseDTO dto,
                                                                 Authentication authentication) {
        userService.changerMotDePasse(authentication.getName(), dto);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    @PutMapping("/double-authentification")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Activer / désactiver la double authentification (code SMS à chaque nouvelle connexion)")
    public ResponseEntity<AuthResponse> doubleAuthentification(@Valid @RequestBody DoubleAuthentification dto) {
        return ResponseEntity.ok(authAvance.doubleAuthentification(dto));
    }

    @GetMapping("/permissions")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mes permissions (pour afficher ou masquer les menus et boutons de l'application)")
    public ResponseEntity<List<com.example.snjpro_backend.dto.RoleDTOs.Permission>> mesPermissions(Authentication authentication) {
        return ResponseEntity.ok(roleService.permissions(((User) authentication.getPrincipal()).getRole()));
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sessions actives (navigateur, application, adresse IP, session actuelle)")
    public ResponseEntity<List<Session>> sessions(HttpServletRequest requete) {
        return ResponseEntity.ok(authAvance.sessions(JwtAuthenticationFilter.parseJwt(requete)));
    }

    @DeleteMapping("/sessions/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Déconnecter une session")
    public ResponseEntity<Void> deconnecterSession(@PathVariable Long id) {
        authAvance.deconnecterSession(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Déconnecter toutes les autres sessions")
    public ResponseEntity<Map<String, Integer>> deconnecterAutres(HttpServletRequest requete) {
        return ResponseEntity.ok(Map.of("sessionsFermees", authAvance.deconnecterAutresSessions(JwtAuthenticationFilter.parseJwt(requete))));
    }

    @GetMapping("/preferences-notifications")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Mes préférences de notification (application / e-mail / SMS par événement)")
    public ResponseEntity<List<Preference>> preferences() {
        return ResponseEntity.ok(authAvance.preferences());
    }

    @PutMapping("/preferences-notifications")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Enregistrer mes préférences de notification")
    public ResponseEntity<List<Preference>> enregistrerPreferences(@RequestBody Preferences dto) {
        return ResponseEntity.ok(authAvance.enregistrerPreferences(dto.getPreferences()));
    }

    /** Permet de se connecter avec son e-mail à la place du nom d'utilisateur. */
    private String identifiantConnexion(String saisie) {
        if (!saisie.contains("@")) return saisie;
        try {
            return userService.obtenirParEmail(saisie).getUsername();
        } catch (Exception e) {
            return saisie;
        }
    }
}
