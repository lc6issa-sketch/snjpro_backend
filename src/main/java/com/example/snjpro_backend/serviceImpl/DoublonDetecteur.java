package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Détection des doublons évidents avant l'enregistrement d'un membre (US03, F16).
 */
@Component
@RequiredArgsConstructor
public class DoublonDetecteur {
    private final MembreRepository membreRepository;

    /**
     * @param exclureId identifiant du membre à ignorer (cas d'une modification), peut être null
     * @return la liste des motifs de doublon trouvés en base (vide si aucun)
     */
    public List<String> motifs(String prenom, String nom, LocalDate dateNaissance,
                               String telephone, String email, Long exclureId) {
        List<String> motifs = new ArrayList<>();
        String tel = normaliserTelephone(telephone);
        if (tel != null) {
            membreRepository.findFirstByNumeroTelephone(tel)
                    .filter(m -> !Objects.equals(m.getId(), exclureId))
                    .ifPresent(m -> motifs.add("Téléphone déjà utilisé par " + m.getIdentifiantSnj()));
        }
        if (StringUtils.hasText(email)) {
            membreRepository.findFirstByEmailIgnoreCase(email.trim())
                    .filter(m -> !Objects.equals(m.getId(), exclureId))
                    .ifPresent(m -> motifs.add("Email déjà utilisé par " + m.getIdentifiantSnj()));
        }
        if (StringUtils.hasText(nom) && StringUtils.hasText(prenom) && dateNaissance != null) {
            membreRepository.findHomonymes(nom.trim(), prenom.trim(), dateNaissance).stream()
                    .filter(m -> !Objects.equals(m.getId(), exclureId))
                    .findFirst()
                    .ifPresent(m -> motifs.add("Même nom, prénom et date de naissance que " + m.getIdentifiantSnj()));
        }
        return motifs;
    }

    /** Supprime espaces et séparateurs pour comparer les numéros de manière fiable. */
    public static String normaliserTelephone(String telephone) {
        if (!StringUtils.hasText(telephone)) return null;
        String t = telephone.replaceAll("[\\s.\\-()]", "");
        return t.isEmpty() ? null : t;
    }

    /** Minuscule, sans accents ni espaces superflus : sert de clé de comparaison des noms. */
    public static String normaliserTexte(String texte) {
        if (texte == null) return "";
        String sansAccents = Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
