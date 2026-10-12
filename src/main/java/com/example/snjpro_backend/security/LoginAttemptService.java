package com.example.snjpro_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les tentatives de connexion abusives (exigence 5.1) :
 * après N échecs consécutifs, le nom d'utilisateur est bloqué pendant une durée donnée.
 */
@Service
public class LoginAttemptService {

    private final int maxTentatives;
    private final Duration dureeBlocage;
    private final Map<String, Tentatives> tentatives = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${app.security.login.max-tentatives:5}") int maxTentatives,
                               @Value("${app.security.login.blocage-minutes:15}") long blocageMinutes) {
        this.maxTentatives = maxTentatives;
        this.dureeBlocage = Duration.ofMinutes(blocageMinutes);
    }

    public boolean estBloque(String username) {
        Tentatives t = tentatives.get(cle(username));
        if (t == null || t.bloqueJusqua == null) return false;
        if (Instant.now().isAfter(t.bloqueJusqua)) {
            tentatives.remove(cle(username));
            return false;
        }
        return true;
    }

    public long minutesRestantes(String username) {
        Tentatives t = tentatives.get(cle(username));
        if (t == null || t.bloqueJusqua == null) return 0;
        return Math.max(1, Duration.between(Instant.now(), t.bloqueJusqua).toMinutes() + 1);
    }

    public void echec(String username) {
        tentatives.compute(cle(username), (k, t) -> {
            Tentatives n = t == null ? new Tentatives() : t;
            n.echecs++;
            if (n.echecs >= maxTentatives) n.bloqueJusqua = Instant.now().plus(dureeBlocage);
            return n;
        });
    }

    public void succes(String username) {
        tentatives.remove(cle(username));
    }

    private String cle(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static class Tentatives {
        int echecs;
        Instant bloqueJusqua;
    }
}
