package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.DecisionDoublon;
import com.example.snjpro_backend.enumeration.Enumerations.StatutImport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTO de l'assistant d'import en plusieurs étapes. */
public final class ImportAssistantDTOs {
    private ImportAssistantDTOs() { }

    /** AUTO : reconnue, A_VERIFIER : correspondance probable, IGNOREE : non reconnue. */
    public enum EtatColonne { AUTO, A_VERIFIER, IGNOREE }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Colonne {
        private String entete;
        private String exemple;
        private String champ;
        private EtatColonne etat;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Champ {
        private String cle;
        private String libelle;
        private boolean obligatoire;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Session {
        private Long id;
        private String nomFichier;
        private long taille;
        private Long cohorteId;
        private String cohorteNom;
        private StatutImport statut;
        private int totalLignes;
        private int importes;
        private int fusionnes;
        private int ignores;
        private int erreurs;
        private int doublons;
        private String creePar;
        private LocalDateTime dateCreation;
        private LocalDateTime dateImport;
    }

    /** Étape 2 : analyse du fichier et correspondance des colonnes. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Analyse {
        private Session session;
        private List<Colonne> colonnes;
        private List<Champ> champsDisponibles;
        private List<String> champsObligatoiresManquants;
        /** Première ligne telle qu'elle sera importée : champ -> valeur. */
        private Map<String, String> apercu;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CorrespondanceRequest {
        /** en-tête du fichier -> clé du champ (null ou vide pour ignorer la colonne). */
        private Map<String, String> correspondance;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErreurLigne {
        private int ligne;
        private String champ;
        private String valeur;
        private String message;
        private String suggestion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Comparaison {
        private String champ;
        private String valeurFichier;
        private String valeurExistante;
        private boolean identique;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DoublonLigne {
        private int ligne;
        private String nomFichier;
        /** null si le doublon est interne au fichier. */
        private Long membreExistantId;
        private String identifiantSnj;
        private String nomExistant;
        private int score;
        private List<String> motifs;
        private List<Comparaison> comparaison;
        private DecisionDoublon decision;
    }

    /** Étape 3 : vérification des données. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Verification {
        private Session session;
        private int totalLignes;
        private int lignesValides;
        private int lignesEnErreur;
        private int doublonsDetectes;
        private int doublonsSansDecision;
        private List<ErreurLigne> erreurs;
        private List<DoublonLigne> doublons;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class DecisionRequest {
        private DecisionDoublon decision;
    }

    /** Étape 4 : résultat de l'import. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resultat {
        private Session session;
        private List<String> identifiantsCrees;
        private List<String> identifiantsFusionnes;
        private List<ErreurLigne> erreurs;
        private String message;
    }
}
