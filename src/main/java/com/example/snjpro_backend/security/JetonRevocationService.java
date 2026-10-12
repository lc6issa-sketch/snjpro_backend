package com.example.snjpro_backend.security;

import com.example.snj_pro_backend.entity.JetonRevoque;
import com.example.snj_pro_backend.repository.JetonRevoqueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;

/**
 * Gère la liste des jetons JWT invalidés (déconnexion).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JetonRevocationService {
    private final JetonRevoqueRepository jetonRevoqueRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public void revoquer(String jwt) {
        String empreinte = empreinte(jwt);
        if (jetonRevoqueRepository.existsByEmpreinte(empreinte)) return;
        Date expiration = jwtUtils.getExpirationFromJwtToken(jwt);
        jetonRevoqueRepository.save(JetonRevoque.builder()
                .empreinte(empreinte)
                .dateExpiration(LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault()))
                .build());
    }

    @Transactional(readOnly = true)
    public boolean estRevoque(String jwt) {
        return jetonRevoqueRepository.existsByEmpreinte(empreinte(jwt));
    }

    /** Purge quotidienne des jetons déjà expirés : ils sont de toute façon refusés. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgerJetonsExpires() {
        int supprimes = jetonRevoqueRepository.supprimerExpires(LocalDateTime.now());
        if (supprimes > 0) log.info("{} jeton(s) révoqué(s) expiré(s) purgé(s)", supprimes);
    }

    /** Révoque un jeton connu seulement par son empreinte (déconnexion d'une autre session). */
    @Transactional
    public void revoquerEmpreinte(String empreinte, LocalDateTime expiration) {
        if (jetonRevoqueRepository.existsByEmpreinte(empreinte)) return;
        jetonRevoqueRepository.save(JetonRevoque.builder().empreinte(empreinte).dateExpiration(expiration).build());
    }

    public static String empreinte(String jwt) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(jwt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
