package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.TypeRapport;
import com.example.snjpro_backend.enumeration.SexeEnum;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StatistiqueDTOs {
    private StatistiqueDTOs() { }

    /** Filtres communs des statistiques. */
    public record Filtres(Integer annee, Long cohorteId, String region, SexeEnum sexe) { }

    public record Libelle(String libelle, long valeur, int pourcentage) { }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Kpi {
        private long valeur;
        private Double evolutionPourcentage;
        private String detail;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActiviteRecente {
        private String titre;
        private String description;
        private String type;
        private String utilisateur;
        private String entite;
        private Long entiteId;
        private LocalDateTime date;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TableauDeBord {
        private Kpi membresEnregistres;
        private long cohortes;
        private Map<String, Long> cohortesParStatut;
        private int pourcentageProfilsComplets;
        private long profilsComplets;
        private Kpi membresMobilisables;
        private long disponiblesCetteSemaine;
        /** Nouveaux membres par année d'incorporation. */
        private Map<Integer, Long> evolutionEffectifs;
        private List<Libelle> repartitionSexe;
        private List<Libelle> repartitionRegions;
        private List<Libelle> topCompetences;
        private long nombreCompetences;
        private List<ActiviteRecente> activiteRecente;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrancheAge {
        private String tranche;
        private long hommes;
        private long femmes;
        private double pourcentageHommes;
        private double pourcentageFemmes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Statistiques {
        private long totalMembres;
        private List<Libelle> repartitionGeographique;
        private List<TrancheAge> pyramideAges;
        private List<Libelle> situationProfessionnelle;
        private long profilsAvecSituation;
        /** Taux moyen de complétude des profils par année d'incorporation. */
        private Map<Integer, Integer> completudeParAnnee;
        private List<Libelle> repartitionSexe;
        private List<Libelle> parCohorte;
    }

    @Data
    public static class DemandeRapport {
        @NotNull
        private TypeRapport type;
        private LocalDate du;
        private LocalDate au;
        private String region;
        private FormatFichier format = FormatFichier.PDF;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RapportDTO {
        private Long id;
        private String titre;
        private TypeRapport type;
        private FormatFichier format;
        private String periode;
        private String region;
        private long taille;
        private String generePar;
        private LocalDateTime dateGeneration;
        private boolean automatique;
        private String urlTelechargement;
    }

    /** Export de la liste des membres. */
    @Data
    public static class Export {
        private FormatFichier format = FormatFichier.XLSX;
        /** IDENTITE, CONTACT, COHORTE, LOCALISATION, SITUATION, COMPETENCES */
        private List<String> sections = List.of("IDENTITE", "CONTACT", "COHORTE", "LOCALISATION", "SITUATION");
        private boolean masquerDonneesSensibles = true;
        private MembreFilterDTO filtres = new MembreFilterDTO();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EstimationExport {
        private long nombreMembres;
        private long tailleEstimeeOctets;
    }
}
