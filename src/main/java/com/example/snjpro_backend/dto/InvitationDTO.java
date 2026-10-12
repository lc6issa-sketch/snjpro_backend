package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.RoleEnum;
import jakarta.validation.constraints.*;
import lombok.Data;

/** Invitation d'un utilisateur : un e-mail d'activation lui est envoyé, il choisit lui-même son mot de passe. */
@Data
public class InvitationDTO {
    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 50)
    private String prenom;
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50)
    private String nom;
    @NotBlank @Email
    private String email;
    @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide")
    private String telephone;
    @NotNull
    private RoleEnum role;
    /** Obligatoire pour ROLE_RESPONSABLE_REGIONAL. */
    private String region;
    /** Obligatoire pour ROLE_RESPONSABLE_COHORTE. */
    private Long cohorteId;
    @Size(max = 500)
    private String message;
    /** Facultatif : nom d'utilisateur choisi par l'administrateur (sinon généré à partir du prénom et du nom). */
    @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Le nom d'utilisateur ne peut contenir que lettres, chiffres, points, tirets et soulignés")
    private String username;
    /**
     * Facultatif : mot de passe défini par l'administrateur. Le compte est alors créé actif,
     * sans e-mail d'invitation ; l'utilisateur pourra le changer dans Paramètres.
     */
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
    private String motDePasse;
}
