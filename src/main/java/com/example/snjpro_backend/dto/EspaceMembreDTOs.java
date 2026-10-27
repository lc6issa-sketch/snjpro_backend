package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.dto.MobilisationDTOs.MaMobilisation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Écrans de l'application mobile du membre : accueil, carte numérique, CV, complétude. */
public final class EspaceMembreDTOs {
    private EspaceMembreDTOs() { }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Completude {
        private int taux;
        private boolean complet;
        private List<String> elementsManquants;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Accueil {
        private MembreResponseDTO membre;
        private Completude completude;
        @Schema(description = "Mobilisation en cours la plus récente qui concerne le membre (null si aucune)")
        private MaMobilisation mobilisationEnCours;
        private long mobilisationsEnAttenteDeReponse;
        private ActualiteDTOs.Resume aLaUne;
        private List<ActualiteDTOs.Resume> actualitesRecentes;
        private List<ActiviteDTOs.Resume> activitesDuJour;
        private long notificationsNonLues;
        private long messagesNonLus;
        private long demandesEnCours;
        private long candidaturesEnCours;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Carte {
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        private String photoUrl;
        private LocalDate dateNaissance;
        private String groupeSanguin;
        private String cohorte;
        private String compagnie;
        private String section;
        private LocalDate dateIncorporation;
        private String statut;
        private String region;
        @Schema(description = "Contenu à encoder dans le QR code (signé) : SNJPRO:MBR:{identifiant}:{signature}")
        private String qrContenu;
        private LocalDate dateEmission;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class VerificationCarte {
        private boolean valide;
        private String message;
        private Long membreId;
        private String identifiantSnj;
        private String nomComplet;
        private String photoUrl;
        private String cohorte;
        private String statut;
    }

    /** Profil visible après le scan de la carte d'un membre : pas de téléphone, e-mail, date de naissance ni sanction. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProfilScanne {
        private boolean valide;
        private String message;
        private Long membreId;
        private String identifiantSnj;
        private String prenom;
        private String nom;
        @Schema(description = "Photo à charger sur /api/cartes/{identifiantSnj}/{signature}/photo")
        private boolean photo;
        private String signature;
        private String cohorte;
        private boolean memeCohorte;
        private String compagnie;
        private String section;
        private LocalDate dateIncorporation;
        private String statut;
        private String region;
        private String profession;
        private String secteurActivite;
        private List<CompetenceDTO> competences;
        private List<ParcoursSNJDTO> parcours;
        @Schema(description = "Mobilisations auxquelles le membre s'est déclaré disponible")
        private long mobilisations;
        @Schema(description = "Le membre a activé son compte : on peut lui écrire dans la messagerie")
        private boolean contactable;
        private boolean enLigne;
        @Schema(description = "La carte scannée est celle de l'utilisateur connecté")
        private boolean moi;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Cv {
        private MembreResponseDTO membre;
        private String titre;
        private List<CompetenceDTO> competences;
        private List<ParcoursSNJDTO> parcours;
        private List<String> engagements;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Disponibilite {
        private boolean disponible;
    }
}
