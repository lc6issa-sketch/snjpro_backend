package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.entity.CodeVerification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeCodeVerification;
import com.example.snjpro_backend.exception.TropDeTentativesException;
import com.example.snjpro_backend.repository.CodeVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Génération et vérification des codes à usage unique (SMS / e-mail).
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CodeVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodeVerificationRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.duree-minutes:10}")
    private int dureeMinutes;
    @Value("${app.otp.max-tentatives:5}")
    private int maxTentatives;
    @Value("${app.otp.exposer-en-reponse:false}")
    private boolean exposerEnReponse;

    /** Crée un code numérique à 6 chiffres ; les codes précédents du même type pour la même destination sont annulés. */
    public String creerCode(TypeCodeVerification type, String destination, Long userId, Long membreId) {
        return creer(type, destination, userId, membreId, String.format("%06d", RANDOM.nextInt(1_000_000)), dureeMinutes);
    }

    /** Crée un jeton long (lien envoyé par e-mail : réinitialisation, invitation). */
    public String creerJetonLien(TypeCodeVerification type, String destination, Long userId, int dureeHeures) {
        byte[] octets = new byte[24];
        RANDOM.nextBytes(octets);
        String jeton = HexFormat.of().formatHex(octets);
        creer(type, destination, userId, null, jeton, dureeHeures * 60);
        repository.findFirstByTypeAndDestinationAndUtiliseFalseOrderByDateCreationDesc(type, destination)
                .ifPresent(c -> c.setJeton(empreinte(jeton)));
        return jeton;
    }

    /**
     * Vérifie le code saisi. En cas de succès, le code est marqué vérifié et un jeton d'étape est renvoyé
     * (utilisé par exemple pour finaliser l'activation d'un compte).
     */
    public CodeVerification verifier(TypeCodeVerification type, String destination, String code) {
        CodeVerification cv = repository.findFirstByTypeAndDestinationAndUtiliseFalseOrderByDateCreationDesc(type, destination)
                .orElseThrow(() -> new IllegalArgumentException("Aucun code en cours de validité : demandez un nouveau code."));
        if (cv.getDateExpiration().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Ce code a expiré : demandez un nouveau code.");
        if (cv.getTentatives() >= maxTentatives)
            throw new TropDeTentativesException("Trop de tentatives : demandez un nouveau code.");
        cv.setTentatives(cv.getTentatives() + 1);
        if (code == null || !passwordEncoder.matches(code.trim(), cv.getCodeHash())) {
            repository.save(cv);
            throw new IllegalArgumentException("Code incorrect. Tentatives restantes : " + (maxTentatives - cv.getTentatives()));
        }
        cv.setVerifie(true);
        byte[] octets = new byte[24];
        RANDOM.nextBytes(octets);
        cv.setJeton(HexFormat.of().formatHex(octets));
        return repository.save(cv);
    }

    /** Vérifie un jeton de lien (e-mail) sans le consommer. */
    public CodeVerification verifierJetonLien(TypeCodeVerification type, String jeton) {
        if (jeton == null || jeton.isBlank()) throw new IllegalArgumentException("Lien invalide ou expiré.");
        return repository.findFirstByJetonAndTypeAndUtiliseFalse(empreinte(jeton.trim()), type)
                .filter(c -> c.getDateExpiration().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("Lien invalide ou expiré."));
    }

    private static String empreinte(String valeur) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(valeur.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Récupère le code vérifié correspondant à un jeton d'étape (ex. finalisation de l'activation). */
    public CodeVerification parJetonEtape(TypeCodeVerification type, String jeton) {
        CodeVerification cv = repository.findFirstByJetonAndTypeAndUtiliseFalse(jeton, type)
                .orElseThrow(() -> new IllegalArgumentException("Jeton invalide ou déjà utilisé : recommencez la vérification."));
        if (!cv.isVerifie() || cv.getDateExpiration().plusMinutes(dureeMinutes).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Jeton expiré : recommencez la vérification.");
        return cv;
    }

    public void consommer(CodeVerification cv) {
        cv.setUtilise(true);
        repository.save(cv);
    }

    /** Réponse standard d'un envoi de code (le code n'est inclus qu'en mode développement). */
    public Map<String, Object> reponseEnvoi(String message, String code) {
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("message", message);
        reponse.put("expireDansMinutes", dureeMinutes);
        if (exposerEnReponse) reponse.put("codeDeveloppement", code);
        return reponse;
    }

    private String creer(TypeCodeVerification type, String destination, Long userId, Long membreId, String code, int minutes) {
        repository.findByTypeAndDestinationAndUtiliseFalse(type, destination).forEach(c -> c.setUtilise(true));
        repository.save(CodeVerification.builder()
                .type(type)
                .destination(destination)
                .codeHash(passwordEncoder.encode(code))
                .userId(userId)
                .membreId(membreId)
                .dateExpiration(LocalDateTime.now().plusMinutes(minutes))
                .build());
        return code;
    }
}
