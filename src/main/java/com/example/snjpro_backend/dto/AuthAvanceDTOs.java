package com.example.snjpro_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** DTO des parcours d'authentification complémentaires (SMS, activation, réinitialisation, sessions…). */
public final class AuthAvanceDTOs {
    private AuthAvanceDTOs() { }

    private static final String MDP_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    private static final String MDP_MESSAGE = "Le mot de passe doit contenir au moins une lettre et un chiffre";

    @Data
    public static class ConnexionTelephone {
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        private String telephone;
        @NotBlank(message = "Le mot de passe est obligatoire")
        private String motDePasse;
    }

    @Data
    public static class DemandeCode {
        @NotBlank
        @Schema(description = "Nom d'utilisateur, e-mail ou numéro de téléphone")
        private String identifiant;
    }

    @Data
    public static class VerificationCodeConnexion {
        @NotBlank
        private String identifiant;
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "Le code comporte 6 chiffres")
        private String code;
        private boolean faireConfianceAppareil;
    }

    @Data
    public static class Telephone {
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        private String telephone;
        @Schema(description = "Facultatif : identifiant SNJ, pour lever une ambiguïté")
        private String identifiantSnj;
    }

    @Data
    public static class Email {
        @NotBlank @jakarta.validation.constraints.Email
        private String email;
    }

    @Data
    public static class VerificationTelephone {
        @NotBlank
        private String telephone;
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "Le code comporte 6 chiffres")
        private String code;
    }

    @Data
    public static class Code {
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "Le code comporte 6 chiffres")
        private String code;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JetonEtape {
        @Schema(description = "Jeton à transmettre à l'étape suivante (valable quelques minutes)")
        private String jeton;
        private String message;
    }

    @Data
    public static class Reinitialisation {
        @NotBlank
        @Schema(description = "Jeton du lien reçu par e-mail ou jeton obtenu après vérification du code SMS")
        private String jeton;
        @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        @Pattern(regexp = MDP_REGEX, message = MDP_MESSAGE)
        private String nouveauMotDePasse;
    }

    @Data
    public static class Activation {
        @NotBlank
        private String jeton;
        @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        @Pattern(regexp = MDP_REGEX, message = MDP_MESSAGE)
        private String motDePasse;
    }

    @Data
    public static class AcceptationInvitation {
        @NotBlank
        private String jeton;
        @Size(min = 3, max = 50)
        @Schema(description = "Facultatif : nom d'utilisateur souhaité")
        private String username;
        @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
        @Pattern(regexp = MDP_REGEX, message = MDP_MESSAGE)
        private String motDePasse;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InfoInvitation {
        private String email;
        private String prenom;
        private String nom;
        private String role;
        private String perimetre;
    }

    @Data
    public static class MonCompte {
        @Size(max = 50)
        private String prenom;
        @Size(max = 50)
        private String nom;
        @NotBlank @jakarta.validation.constraints.Email
        private String email;
        @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide")
        private String telephone;
    }

    @Data
    public static class DoubleAuthentification {
        @NotNull
        private Boolean actif;
        @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide")
        private String telephone;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Session {
        private Long id;
        private String appareil;
        private String adresseIp;
        private LocalDateTime dateCreation;
        private boolean sessionActuelle;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Preference {
        private String evenement;
        private String libelle;
        private boolean application;
        private boolean email;
        private boolean sms;
    }

    @Data
    public static class Preferences {
        private List<Preference> preferences;
    }
}
