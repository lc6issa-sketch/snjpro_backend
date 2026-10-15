package com.example.snjpro_backend.serviceImpl;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Référentiel des diplômes, du moins au plus élevé : sert à déduire le dernier diplôme
 * (le plus élevé) à partir de la liste des diplômes d'un membre.
 */
public final class Diplomes {

    /** Ordre croissant de niveau ; « Autre » n'a pas de rang. */
    public static final List<String> REFERENTIEL = List.of("Aucun", "CEP", "DEF", "CAP", "BT", "BAC", "DUT / BTS",
            "Licence", "Master", "Doctorat", "Autre");

    private Diplomes() {}

    /** Liste sans valeurs vides ni doublons (casse ignorée), dans l'ordre de saisie. */
    public static List<String> nettoyer(Collection<String> diplomes) {
        List<String> l = new ArrayList<>();
        if (diplomes == null) return l;
        for (String d : diplomes) {
            if (!StringUtils.hasText(d)) continue;
            String v = d.trim();
            if (l.stream().noneMatch(x -> x.equalsIgnoreCase(v))) l.add(v);
        }
        return l;
    }

    /** Diplôme le plus élevé selon le référentiel ; à défaut, le dernier saisi. */
    public static String plusEleve(List<String> diplomes) {
        if (diplomes == null || diplomes.isEmpty()) return null;
        String meilleur = null;
        int rangMeilleur = -1;
        for (String d : diplomes) {
            int rang = "Autre".equalsIgnoreCase(d) ? -1 : REFERENTIEL.indexOf(d);
            if (rang > rangMeilleur) { rangMeilleur = rang; meilleur = d; }
        }
        return meilleur != null ? meilleur : diplomes.get(diplomes.size() - 1);
    }
}
