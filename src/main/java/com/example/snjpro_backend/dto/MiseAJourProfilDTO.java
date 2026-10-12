package com.example.snjpro_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Champs qu'un membre est autorisé à modifier lui-même (F17 / US05).
 * L'identité (nom, prénom, sexe, date de naissance), la cohorte et le statut restent gérés par l'administration.
 */
@Data
public class MiseAJourProfilDTO {
    @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide (ex : +223 70 00 00 00)")
    private String numeroTelephone;
    @Email(message = "Adresse email invalide")
    @Size(max = 100)
    private String email;
    @Size(max = 255)
    private String photoUrl;
}
