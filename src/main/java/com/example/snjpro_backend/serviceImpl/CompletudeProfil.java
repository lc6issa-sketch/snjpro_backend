package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.entity.Membre;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcul du taux de complétude d'un profil (KPI « profils complets ») et des éléments manquants.
 * Un profil est considéré « complet » à partir de {@link #SEUIL_COMPLET} %.
 */
public final class CompletudeProfil {
    public static final int SEUIL_COMPLET = 80;
    private static final int CRITERES = 9;

    private CompletudeProfil() { }

    public static int taux(Membre m) {
        return (int) Math.round((CRITERES - manquants(m).size()) * 100.0 / CRITERES);
    }

    public static boolean complet(Membre m) { return taux(m) >= SEUIL_COMPLET; }

    /** Libellés des éléments à compléter, dans l'ordre d'affichage. */
    public static List<String> manquants(Membre m) {
        List<String> l = new ArrayList<>();
        if (!StringUtils.hasText(m.getPhotoUrl())) l.add("Photo d'identité récente");
        if (!StringUtils.hasText(m.getNumeroTelephone())) l.add("Numéro de téléphone");
        if (!StringUtils.hasText(m.getEmail())) l.add("Adresse e-mail");
        if (m.getDateNaissance() == null) l.add("Date de naissance");
        if (!StringUtils.hasText(m.getLieuNaissance())) l.add("Lieu de naissance");
        if (m.getSituationProfessionnelle() == null) l.add("Situation professionnelle");
        if (m.getLocalisation() == null) l.add("Localisation");
        if (m.getCompetences() == null || m.getCompetences().isEmpty()) l.add("Compétences");
        if (m.getParcours() == null || m.getParcours().isEmpty()) l.add("Parcours SNJ");
        return l;
    }
}
