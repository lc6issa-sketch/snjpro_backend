package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 50)
    private String username;
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Le mot de passe doit contenir au moins une lettre et un chiffre")
    private String password;
    @NotBlank @Email
    private String email;
    private String prenom;
    private String nom;
    @NotNull
    private RoleEnum role;
    /** Obligatoire pour ROLE_RESPONSABLE_REGIONAL. */
    private String region;
    /** Obligatoire pour ROLE_RESPONSABLE_COHORTE. */
    private Long cohorteId;
    //noob saybot
}
