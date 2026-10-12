package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.AuthAvanceDTOs.*;
import com.example.snjpro_backend.dto.AuthResponse;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.InvitationDTO;
import com.example.snjpro_backend.dto.UserResponseDTO;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.TypeCodeVerification;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.exception.TropDeTentativesException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.JetonRevocationService;
import com.example.snjpro_backend.security.JwtUtils;
import com.example.snjpro_backend.security.LoginAttemptService;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.AuthAvanceServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import com.example.snjpro_backend.serviceInterface.UserServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthAvanceServiceImpl implements AuthAvanceServiceInterface {
    private static final String ENTITE = "UTILISATEUR";
    private static final String REPONSE_NEUTRE = "Si un compte correspond, un message vient d'être envoyé.";
    private static final Map<String, String> EVENEMENTS = new LinkedHashMap<>();
    static {
        EVENEMENTS.put("NOUVELLE_DEMANDE", "Nouvelle demande d'un membre");
        EVENEMENTS.put("DOUBLONS_IMPORT", "Doublons détectés lors d'un import");
        EVENEMENTS.put("REPONSES_MOBILISATION", "Réponses à une mobilisation");
        EVENEMENTS.put("MOBILISATION_URGENTE", "Mobilisation urgente");
        EVENEMENTS.put("INVITATION_ACCEPTEE", "Invitation acceptée");
        EVENEMENTS.put("RAPPORT_MENSUEL", "Rapport mensuel disponible");
        EVENEMENTS.put("MOBILISATIONS", "Mobilisations");
        EVENEMENTS.put("ACTUALITES", "Actualités");
    }
    /** Événements proposés aux membres (application mobile) ; les autres concernent le personnel (web). */
    private static final Set<String> EVENEMENTS_MEMBRE = Set.of("MOBILISATIONS", "ACTUALITES");

    private final UserRepository userRepository;
    private final MembreRepository membreRepository;
    private final CohorteRepository cohorteRepository;
    private final SessionUtilisateurRepository sessionRepository;
    private final PreferenceNotificationRepository preferenceRepository;
    private final CodeVerificationService codes;
    private final EnvoiMessageService envoi;
    private final StockageFichierService stockage;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final JwtUtils jwtUtils;
    private final JetonRevocationService revocation;
    private final PasswordEncoder passwordEncoder;
    private final PerimetreService perimetre;
    private final UserServiceInterface userService;
    private final NotificationServiceInterface notificationService;
    private final HistoriqueServiceInterface historiqueService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    // =====================================================================
    // Émission des jetons et sessions
    // =====================================================================

    @Override
    public AuthResponse apresAuthentification(User user, HttpServletRequest requete) {
        if (Boolean.TRUE.equals(user.getDoubleAuthentification())) {
            String tel = telephoneDe(user);
            if (tel == null) return emettreJeton(user, requete);
            String code = codes.creerCode(TypeCodeVerification.CONNEXION, cleUser(user), user.getId(), null);
            envoi.sms(tel, "SNJPro : votre code de connexion est " + code + ". Ne le communiquez à personne.");
            return AuthResponse.builder().deuxiemeFacteurRequis(true).username(user.getUsername())
                    .telephoneMasque(masquer(tel)).message("Saisissez le code envoyé par SMS au " + masquer(tel) + ".").build();
        }
        return emettreJeton(user, requete);
    }

    @Override
    public AuthResponse emettreJeton(User user, HttpServletRequest requete) {
        if (!user.isActif()) throw new BadCredentialsException("Compte désactivé");
        String jwt = jwtUtils.generateTokenFromUsername(user.getUsername());
        sessionRepository.save(SessionUtilisateur.builder()
                .userId(user.getId()).empreinte(JetonRevocationService.empreinte(jwt))
                .appareil(appareil(requete == null ? null : requete.getHeader("User-Agent")))
                .adresseIp(requete == null ? null : ip(requete))
                .dateCreation(LocalDateTime.now())
                .dateExpiration(LocalDateTime.ofInstant(jwtUtils.getExpirationFromJwtToken(jwt).toInstant(), ZoneId.systemDefault()))
                .active(true).build());
        userService.enregistrerConnexion(user.getId());
        historiqueService.enregistrer("CONNEXION", ENTITE, user.getId(), requete == null ? null : appareil(requete.getHeader("User-Agent")), user.getUsername());
        AuthResponse r = AuthResponse.of(jwt, jwtUtils.getExpirationMs(), user);
        membreRepository.findByUserId(user.getId()).ifPresent(m -> r.setMembreId(m.getId()));
        return r;
    }

    @Override
    public void fermerSession(String jwt) {
        if (jwt == null) return;
        sessionRepository.findFirstByEmpreinte(JetonRevocationService.empreinte(jwt)).ifPresent(s -> s.setActive(false));
    }

    // =====================================================================
    // Connexion par téléphone et par code SMS
    // =====================================================================

    @Override
    public AuthResponse connexionTelephone(ConnexionTelephone dto, HttpServletRequest requete) {
        String cle = "tel:" + DoublonDetecteur.normaliserTelephone(dto.getTelephone());
        if (loginAttemptService.estBloque(cle))
            throw new TropDeTentativesException("Trop de tentatives échouées. Réessayez dans " + loginAttemptService.minutesRestantes(cle) + " minute(s).");
        User user = parTelephone(dto.getTelephone()).orElse(null);
        if (user == null) {
            loginAttemptService.echec(cle);
            throw new BadCredentialsException("Identifiants incorrects");
        }
        try {
            Authentication a = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), dto.getMotDePasse()));
            loginAttemptService.succes(cle);
            return apresAuthentification((User) a.getPrincipal(), requete);
        } catch (BadCredentialsException e) {
            loginAttemptService.echec(cle);
            historiqueService.enregistrer("CONNEXION_ECHOUEE", ENTITE, user.getId(), "Connexion par téléphone", user.getUsername());
            throw e;
        }
    }

    @Override
    public Map<String, Object> demanderCodeConnexion(DemandeCode dto) {
        Optional<User> user = parIdentifiant(dto.getIdentifiant()).filter(User::isActif);
        String tel = user.map(this::telephoneDe).orElse(null);
        if (user.isEmpty() || tel == null)
            return codes.reponseEnvoi(REPONSE_NEUTRE, null);
        String code = codes.creerCode(TypeCodeVerification.CONNEXION, cleUser(user.get()), user.get().getId(), null);
        envoi.sms(tel, "SNJPro : votre code de connexion est " + code + ". Ne le communiquez à personne.");
        Map<String, Object> r = codes.reponseEnvoi("Code envoyé par SMS au " + masquer(tel) + ".", code);
        r.put("telephoneMasque", masquer(tel));
        return r;
    }

    @Override
    public AuthResponse verifierCodeConnexion(VerificationCodeConnexion dto, HttpServletRequest requete) {
        User user = parIdentifiant(dto.getIdentifiant()).filter(User::isActif)
                .orElseThrow(() -> new IllegalArgumentException("Code incorrect ou expiré."));
        CodeVerification cv = codes.verifier(TypeCodeVerification.CONNEXION, cleUser(user), dto.getCode());
        codes.consommer(cv);
        return emettreJeton(user, requete);
    }

    // =====================================================================
    // Mot de passe oublié
    // =====================================================================

    @Override
    public Map<String, Object> motDePasseOublieEmail(Email dto) {
        userRepository.findByEmail(dto.getEmail().trim().toLowerCase()).or(() -> userRepository.findByEmail(dto.getEmail().trim()))
                .filter(User::isActif)
                .ifPresent(u -> {
                    String jeton = codes.creerJetonLien(TypeCodeVerification.REINITIALISATION, cleUser(u), u.getId(), 1);
                    envoi.email(u.getEmail(), "SNJPro — Réinitialisation de votre mot de passe",
                            "Pour choisir un nouveau mot de passe, ouvrez ce lien (valable 1 heure) : "
                                    + frontendUrl + "/reinitialiser-mot-de-passe?jeton=" + jeton);
                    historiqueService.enregistrer("DEMANDE_REINITIALISATION", ENTITE, u.getId(), "Par e-mail", u.getUsername());
                });
        return codes.reponseEnvoi(REPONSE_NEUTRE, null);
    }

    @Override
    public Map<String, Object> motDePasseOublieSms(Telephone dto) {
        String tel = DoublonDetecteur.normaliserTelephone(dto.getTelephone());
        Optional<User> u = parTelephone(tel).filter(User::isActif);
        if (u.isEmpty()) return codes.reponseEnvoi(REPONSE_NEUTRE, null);
        String code = codes.creerCode(TypeCodeVerification.REINITIALISATION, "tel:" + tel, u.get().getId(), null);
        envoi.sms(tel, "SNJPro : code de réinitialisation " + code + " (valable 10 minutes).");
        return codes.reponseEnvoi("Code envoyé par SMS au " + masquer(tel) + ".", code);
    }

    @Override
    public JetonEtape verifierCodeReinitialisation(VerificationTelephone dto) {
        CodeVerification cv = codes.verifier(TypeCodeVerification.REINITIALISATION,
                "tel:" + DoublonDetecteur.normaliserTelephone(dto.getTelephone()), dto.getCode());
        return JetonEtape.builder().jeton(cv.getJeton()).message("Code vérifié : choisissez votre nouveau mot de passe.").build();
    }

    @Override
    public Map<String, String> reinitialiser(Reinitialisation dto) {
        CodeVerification cv;
        try {
            cv = codes.verifierJetonLien(TypeCodeVerification.REINITIALISATION, dto.getJeton());
        } catch (IllegalArgumentException e) {
            cv = codes.parJetonEtape(TypeCodeVerification.REINITIALISATION, dto.getJeton());
        }
        User u = userRepository.findById(cv.getUserId()).orElseThrow(() -> new EntityNotFoundException("Compte introuvable."));
        u.setPassword(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        userRepository.save(u);
        codes.consommer(cv);
        historiqueService.enregistrer("REINITIALISATION_MOT_DE_PASSE", ENTITE, u.getId(), "Par le titulaire du compte", u.getUsername());
        return Map.of("message", "Mot de passe modifié : vous pouvez vous connecter.");
    }

    // =====================================================================
    // Activation du compte d'un membre
    // =====================================================================

    @Override
    public Map<String, Object> demanderActivation(Telephone dto) {
        String tel = DoublonDetecteur.normaliserTelephone(dto.getTelephone());
        Membre m = membreRepository.findFirstByNumeroTelephone(tel)
                .filter(x -> !StringUtils.hasText(dto.getIdentifiantSnj()) || x.getIdentifiantSnj().equalsIgnoreCase(dto.getIdentifiantSnj().trim()))
                .orElseThrow(() -> new EntityNotFoundException("Ce numéro n'est associé à aucun membre du SNJ. "
                        + "Contactez votre direction régionale pour le mettre à jour."));
        if (m.getUserId() != null && userRepository.existsById(m.getUserId()))
            throw new ConflitException("Ce compte est déjà activé : connectez-vous ou utilisez « Mot de passe oublié ».");
        String code = codes.creerCode(TypeCodeVerification.ACTIVATION, "tel:" + tel, null, m.getId());
        envoi.sms(tel, "SNJPro : votre code d'activation est " + code + " (valable 10 minutes).");
        return codes.reponseEnvoi("Code envoyé par SMS au " + masquer(tel) + ".", code);
    }

    @Override
    public JetonEtape verifierActivation(VerificationTelephone dto) {
        CodeVerification cv = codes.verifier(TypeCodeVerification.ACTIVATION,
                "tel:" + DoublonDetecteur.normaliserTelephone(dto.getTelephone()), dto.getCode());
        return JetonEtape.builder().jeton(cv.getJeton()).message("Numéro vérifié : créez votre mot de passe.").build();
    }

    @Override
    public AuthResponse finaliserActivation(Activation dto, HttpServletRequest requete) {
        CodeVerification cv = codes.parJetonEtape(TypeCodeVerification.ACTIVATION, dto.getJeton());
        Membre m = membreRepository.findById(cv.getMembreId()).orElseThrow(() -> new EntityNotFoundException("Fiche membre introuvable."));
        if (m.getUserId() != null && userRepository.existsById(m.getUserId())) throw new ConflitException("Ce compte est déjà activé.");
        String username = m.getIdentifiantSnj().toLowerCase();
        String email = StringUtils.hasText(m.getEmail()) && !userRepository.existsByEmail(m.getEmail())
                ? m.getEmail() : username + "@membres.snjpro.ml";
        User u = userRepository.save(User.builder()
                .username(username).password(passwordEncoder.encode(dto.getMotDePasse())).email(email)
                .prenom(m.getPrenom()).nom(m.getNom()).telephone(m.getNumeroTelephone())
                .role(RoleEnum.ROLE_MEMBRE).actif(true).createdBy("activation").build());
        m.setUserId(u.getId());
        m.setTelephoneVerifie(true);
        membreRepository.save(m);
        codes.consommer(cv);
        historiqueService.enregistrer("ACTIVATION_COMPTE", "MEMBRE", m.getId(), m.getIdentifiantSnj(), username);
        return emettreJeton(u, requete);
    }

    // =====================================================================
    // Invitations
    // =====================================================================

    @Override
    public UserResponseDTO inviter(InvitationDTO dto) {
        String email = dto.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) throw new ConflitException("L'email '" + email + "' est déjà utilisé.");
        if (dto.getRole() == RoleEnum.ROLE_MEMBRE)
            throw new IllegalArgumentException("Les membres activent eux-mêmes leur compte depuis l'application mobile.");
        if (dto.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL && !StringUtils.hasText(dto.getRegion()))
            throw new IllegalArgumentException("La région est obligatoire pour un responsable régional.");
        if (dto.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE && (dto.getCohorteId() == null || !cohorteRepository.existsById(dto.getCohorteId())))
            throw new IllegalArgumentException("Une cohorte existante est obligatoire pour un responsable de cohorte.");
        String username;
        if (StringUtils.hasText(dto.getUsername())) {
            username = dto.getUsername().trim();
            if (userRepository.existsByUsername(username))
                throw new ConflitException("Le nom d'utilisateur '" + username + "' est déjà utilisé.");
        } else {
            String base = (dto.getPrenom().trim().charAt(0) + "." + dto.getNom().trim()).toLowerCase()
                    .replaceAll("[^a-z.]", "");
            username = base;
            for (int i = 2; userRepository.existsByUsername(username); i++) username = base + i;
        }
        // Mot de passe fourni : compte actif tout de suite, sans e-mail d'invitation
        boolean creationDirecte = StringUtils.hasText(dto.getMotDePasse());
        String motDePasse = dto.getMotDePasse();
        if (!creationDirecte) {
            byte[] aleatoire = new byte[18];
            new SecureRandom().nextBytes(aleatoire);
            motDePasse = HexFormat.of().formatHex(aleatoire);
        }
        User u = userRepository.save(User.builder()
                .username(username).password(passwordEncoder.encode(motDePasse))
                .email(email).prenom(dto.getPrenom().trim()).nom(dto.getNom().trim())
                .telephone(DoublonDetecteur.normaliserTelephone(dto.getTelephone()))
                .role(dto.getRole())
                .region(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? dto.getRegion() : null)
                .cohorteId(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE ? dto.getCohorteId() : null)
                .actif(creationDirecte).invitationEnAttente(!creationDirecte)
                .createdBy(perimetre.utilisateurCourant().getUsername()).build());
        if (creationDirecte) {
            historiqueService.enregistrer("CREATION_COMPTE", ENTITE, u.getId(), "Rôle : " + u.getRole() + " · " + username);
        } else {
            envoyerInvitation(u, dto.getMessage());
            historiqueService.enregistrer("INVITATION", ENTITE, u.getId(), "Rôle : " + u.getRole() + " · " + email);
        }
        return userService.obtenirUtilisateur(u.getId());
    }

    @Override @Transactional(readOnly = true)
    public List<UserResponseDTO> invitationsEnAttente() {
        return userRepository.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getInvitationEnAttente()))
                .map(u -> userService.obtenirUtilisateur(u.getId())).toList();
    }

    @Override
    public UserResponseDTO renvoyerInvitation(Long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + userId));
        if (!Boolean.TRUE.equals(u.getInvitationEnAttente())) throw new ConflitException("Cette invitation a déjà été acceptée.");
        envoyerInvitation(u, null);
        return userService.obtenirUtilisateur(userId);
    }

    @Override @Transactional(readOnly = true)
    public InfoInvitation infoInvitation(String jeton) {
        User u = utilisateurInvite(codes.verifierJetonLien(TypeCodeVerification.INVITATION, jeton));
        return InfoInvitation.builder().email(u.getEmail()).prenom(u.getPrenom()).nom(u.getNom()).role(u.getRole().name())
                .perimetre(u.getRegion() != null ? "Région de " + u.getRegion()
                        : u.getCohorteId() != null ? cohorteRepository.findById(u.getCohorteId()).map(Cohorte::getNom).orElse(null) : "National")
                .build();
    }

    @Override
    public AuthResponse accepterInvitation(AcceptationInvitation dto, HttpServletRequest requete) {
        CodeVerification cv = codes.verifierJetonLien(TypeCodeVerification.INVITATION, dto.getJeton());
        User u = utilisateurInvite(cv);
        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().trim().equals(u.getUsername())) {
            if (userRepository.existsByUsername(dto.getUsername().trim()))
                throw new ConflitException("Le nom d'utilisateur '" + dto.getUsername().trim() + "' est déjà utilisé.");
            u.setUsername(dto.getUsername().trim());
        }
        u.setPassword(passwordEncoder.encode(dto.getMotDePasse()));
        u.setActif(true);
        u.setInvitationEnAttente(false);
        userRepository.save(u);
        codes.consommer(cv);
        notificationService.alerterRoles(List.of(RoleEnum.ROLE_ADMIN_SYSTEME), null, "Invitation acceptée",
                nom(u) + " a rejoint SNJPro", "INVITATION", "UTILISATEUR", u.getId());
        historiqueService.enregistrer("INVITATION_ACCEPTEE", ENTITE, u.getId(), null, u.getUsername());
        return emettreJeton(u, requete);
    }

    // =====================================================================
    // Mon compte
    // =====================================================================

    @Override
    public AuthResponse modifierMonCompte(MonCompte dto) {
        User u = courant();
        String email = dto.getEmail().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && userRepository.existsByEmail(email))
            throw new ConflitException("L'email '" + email + "' est déjà utilisé.");
        u.setPrenom(dto.getPrenom());
        u.setNom(dto.getNom());
        u.setEmail(email);
        u.setTelephone(DoublonDetecteur.normaliserTelephone(dto.getTelephone()));
        historiqueService.enregistrer("MODIFICATION_COMPTE", ENTITE, u.getId(), null);
        return AuthResponse.of(null, null, userRepository.save(u));
    }

    @Override
    public AuthResponse changerPhoto(MultipartFile photo) {
        if (!StockageFichierService.typeMime(photo.getOriginalFilename()).startsWith("image/"))
            throw new IllegalArgumentException("La photo doit être une image (PNG, JPG, GIF ou WEBP).");
        User u = courant();
        String ancienne = u.getPhotoUrl();
        u.setPhotoUrl(stockage.enregistrer(photo, "photos/utilisateurs"));
        if (ancienne != null && !ancienne.startsWith("http")) stockage.supprimer(ancienne);
        return AuthResponse.of(null, null, userRepository.save(u));
    }

    @Override @Transactional(readOnly = true)
    public Fichier maPhoto() { return photo(courant()); }

    @Override @Transactional(readOnly = true)
    public Fichier photoUtilisateur(Long userId) {
        return photo(userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + userId)));
    }

    @Override
    public AuthResponse doubleAuthentification(DoubleAuthentification dto) {
        User u = courant();
        if (StringUtils.hasText(dto.getTelephone())) u.setTelephone(DoublonDetecteur.normaliserTelephone(dto.getTelephone()));
        if (dto.getActif() && telephoneDe(u) == null)
            throw new IllegalArgumentException("Renseignez un numéro de téléphone pour recevoir les codes.");
        u.setDoubleAuthentification(dto.getActif());
        historiqueService.enregistrer(dto.getActif() ? "ACTIVATION_2FA" : "DESACTIVATION_2FA", ENTITE, u.getId(), null);
        AuthResponse r = AuthResponse.of(null, null, userRepository.save(u));
        r.setTelephoneMasque(masquer(telephoneDe(u)));
        return r;
    }

    @Override @Transactional(readOnly = true)
    public List<Session> sessions(String jwtCourant) {
        String courante = jwtCourant == null ? null : JetonRevocationService.empreinte(jwtCourant);
        return sessionRepository.findByUserIdAndActiveTrueAndDateExpirationAfterOrderByDateCreationDesc(courant().getId(), LocalDateTime.now())
                .stream()
                .map(s -> Session.builder().id(s.getId()).appareil(s.getAppareil()).adresseIp(masquerIp(s.getAdresseIp()))
                        .dateCreation(s.getDateCreation()).sessionActuelle(s.getEmpreinte().equals(courante)).build())
                .toList();
    }

    @Override
    public void deconnecterSession(Long id) {
        SessionUtilisateur s = sessionRepository.findById(id).filter(x -> x.getUserId().equals(courant().getId()))
                .orElseThrow(() -> new EntityNotFoundException("Session non trouvée : " + id));
        revoquer(s);
    }

    @Override
    public int deconnecterAutresSessions(String jwtCourant) {
        String courante = jwtCourant == null ? "" : JetonRevocationService.empreinte(jwtCourant);
        List<SessionUtilisateur> autres = sessionRepository
                .findByUserIdAndActiveTrueAndDateExpirationAfterOrderByDateCreationDesc(courant().getId(), LocalDateTime.now())
                .stream().filter(s -> !s.getEmpreinte().equals(courante)).toList();
        autres.forEach(this::revoquer);
        historiqueService.enregistrer("DECONNEXION_AUTRES_SESSIONS", ENTITE, courant().getId(), autres.size() + " session(s)");
        return autres.size();
    }

    @Override @Transactional(readOnly = true)
    public List<Preference> preferences() {
        Map<String, PreferenceNotification> existantes = new HashMap<>();
        User u = courant();
        preferenceRepository.findByUserId(u.getId()).forEach(p -> existantes.put(p.getEvenement(), p));
        List<Preference> l = new ArrayList<>();
        EVENEMENTS.forEach((code, libelle) -> {
            if (!evenementPropose(u, code)) return;
            PreferenceNotification p = existantes.get(code);
            l.add(Preference.builder().evenement(code).libelle(libelle)
                    .application(p == null || p.isApplication())
                    .email(p != null ? p.isEmail() : !code.equals("REPONSES_MOBILISATION") && !code.equals("INVITATION_ACCEPTEE"))
                    .sms(p != null ? p.isSms() : code.equals("MOBILISATION_URGENTE"))
                    .build());
        });
        return l;
    }

    @Override
    public List<Preference> enregistrerPreferences(List<Preference> preferences) {
        User u = courant();
        Long userId = u.getId();
        Map<String, PreferenceNotification> existantes = new HashMap<>();
        preferenceRepository.findByUserId(userId).forEach(p -> existantes.put(p.getEvenement(), p));
        for (Preference p : preferences) {
            if (!EVENEMENTS.containsKey(p.getEvenement()) || !evenementPropose(u, p.getEvenement()))
                throw new IllegalArgumentException("Événement inconnu : " + p.getEvenement());
            PreferenceNotification e = existantes.getOrDefault(p.getEvenement(),
                    PreferenceNotification.builder().userId(userId).evenement(p.getEvenement()).build());
            e.setApplication(p.isApplication());
            e.setEmail(p.isEmail());
            e.setSms(p.isSms());
            preferenceRepository.save(e);
        }
        return preferences();
    }

    // =====================================================================
    // Vérification du numéro d'un membre
    // =====================================================================

    @Override
    public Map<String, Object> envoyerCodeTelephone() {
        Membre m = membreRepository.findById(perimetre.membreIdCourant()).orElseThrow();
        if (!StringUtils.hasText(m.getNumeroTelephone())) throw new IllegalArgumentException("Renseignez d'abord votre numéro de téléphone.");
        String code = codes.creerCode(TypeCodeVerification.VERIFICATION_TELEPHONE, "tel:" + m.getNumeroTelephone(), null, m.getId());
        envoi.sms(m.getNumeroTelephone(), "SNJPro : code de vérification " + code + " (valable 10 minutes).");
        return codes.reponseEnvoi("Code envoyé par SMS au " + masquer(m.getNumeroTelephone()) + ".", code);
    }

    @Override
    public Map<String, Object> verifierTelephone(Code dto) {
        Membre m = membreRepository.findById(perimetre.membreIdCourant()).orElseThrow();
        CodeVerification cv = codes.verifier(TypeCodeVerification.VERIFICATION_TELEPHONE, "tel:" + m.getNumeroTelephone(), dto.getCode());
        codes.consommer(cv);
        m.setTelephoneVerifie(true);
        membreRepository.save(m);
        historiqueService.enregistrer("VERIFICATION_TELEPHONE", "MEMBRE", m.getId(), m.getIdentifiantSnj());
        return Map.of("message", "Numéro vérifié.", "telephoneVerifie", true);
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void envoyerInvitation(User u, String message) {
        String jeton = codes.creerJetonLien(TypeCodeVerification.INVITATION, cleUser(u), u.getId(), 72);
        envoi.email(u.getEmail(), "Invitation à rejoindre SNJPro",
                (StringUtils.hasText(message) ? message + "\n\n" : "") + "Activez votre compte (lien valable 72 heures) : "
                        + frontendUrl + "/invitation?jeton=" + jeton);
    }

    private User utilisateurInvite(CodeVerification cv) {
        User u = userRepository.findById(cv.getUserId()).orElseThrow(() -> new EntityNotFoundException("Invitation introuvable."));
        if (!Boolean.TRUE.equals(u.getInvitationEnAttente())) throw new ConflitException("Cette invitation a déjà été acceptée.");
        return u;
    }

    private void revoquer(SessionUtilisateur s) {
        revocation.revoquerEmpreinte(s.getEmpreinte(), s.getDateExpiration());
        s.setActive(false);
        sessionRepository.save(s);
    }

    private Fichier photo(User u) {
        if (!StringUtils.hasText(u.getPhotoUrl()) || u.getPhotoUrl().startsWith("http"))
            throw new EntityNotFoundException("Aucune photo.");
        return new Fichier(stockage.lire(u.getPhotoUrl()), "photo", StockageFichierService.typeMime(u.getPhotoUrl()));
    }

    private Optional<User> parTelephone(String telephone) {
        String tel = DoublonDetecteur.normaliserTelephone(telephone);
        if (tel == null) return Optional.empty();
        Optional<User> viaMembre = membreRepository.findFirstByNumeroTelephone(tel)
                .filter(m -> m.getUserId() != null).flatMap(m -> userRepository.findById(m.getUserId()));
        if (viaMembre.isPresent()) return viaMembre;
        return userRepository.findAll().stream().filter(u -> tel.equals(u.getTelephone())).findFirst();
    }

    private Optional<User> parIdentifiant(String identifiant) {
        String id = identifiant.trim();
        return userRepository.findByUsername(id)
                .or(() -> userRepository.findByEmail(id.toLowerCase()))
                .or(() -> parTelephone(id));
    }

    private String telephoneDe(User u) {
        if (StringUtils.hasText(u.getTelephone())) return u.getTelephone();
        return membreRepository.findByUserId(u.getId()).map(Membre::getNumeroTelephone).filter(StringUtils::hasText).orElse(null);
    }

    /** Un membre ne règle que ses événements (mobilisations, actualités) ; le personnel, ceux du back-office. */
    private static boolean evenementPropose(User u, String code) {
        return (u.getRole() == RoleEnum.ROLE_MEMBRE) == EVENEMENTS_MEMBRE.contains(code);
    }

    private User courant() {
        return userRepository.findById(perimetre.utilisateurCourant().getId()).orElseThrow();
    }

    private static String cleUser(User u) { return "user:" + u.getId(); }

    private static String nom(User u) {
        String n = (Objects.toString(u.getPrenom(), "") + " " + Objects.toString(u.getNom(), "")).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    static String masquer(String tel) {
        if (tel == null || tel.length() < 6) return tel;
        return tel.substring(0, tel.length() - 6) + " •• •• " + tel.substring(tel.length() - 2);
    }

    private static String masquerIp(String ip) {
        if (ip == null || !ip.contains(".")) return ip;
        String[] p = ip.split("\\.");
        return p.length == 4 ? p[0] + "." + p[1] + ".•••." + p[3] : ip;
    }

    private static String ip(HttpServletRequest r) {
        String f = r.getHeader("X-Forwarded-For");
        return StringUtils.hasText(f) ? f.split(",")[0].trim() : r.getRemoteAddr();
    }

    static String appareil(String ua) {
        if (ua == null) return "Appareil inconnu";
        if (ua.contains("SNJPro")) return "Application SNJPro · " + (ua.contains("iPhone") || ua.contains("iOS") ? "iOS" : "Android");
        String nav = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox") ? "Firefox" : ua.contains("Chrome") ? "Chrome"
                : ua.contains("Safari") ? "Safari" : ua.contains("okhttp") || ua.contains("Dart") ? "Application mobile" : "Navigateur";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android" : ua.contains("iPhone") ? "iOS"
                : ua.contains("Mac OS") ? "macOS" : ua.contains("Linux") ? "Linux" : "";
        return os.isEmpty() ? nav : nav + " · " + os;
    }
}
