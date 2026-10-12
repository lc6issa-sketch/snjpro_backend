package com.example.snjpro_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Création du compte de connexion (rôle MEMBRE) associé à une fiche membre.
 */
@Data
public class CompteMembreDTO {
    @Size(min = 3, max = 50)
    private String username;
    @NotBlank(message = "Le mot de passe initial est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
    private String password;
    /** Facultatif : l'email de la fiche membre est utilisé par défaut. */
    @Email
    private String email;
}
