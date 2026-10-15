package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class MembreDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Généré automatiquement à la création")
    private String identifiantSnj;
    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 50)
    private String prenom;
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 50)
    private String nom;
    @NotNull(message = "Le sexe est obligatoire")
    private SexeEnum sexe;
    @Past(message = "La date de naissance doit être dans le passé")
    private LocalDate dateNaissance;
    @Size(max = 100)
    private String lieuNaissance;
    @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide (ex : +223 70 00 00 00)")
    private String numeroTelephone;
    @Email(message = "Adresse email invalide")
    @Size(max = 100)
    private String email;
    private String photoUrl;
    @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de téléphone invalide")
    private String telephoneSecondaire;
    /** Nom de la personne à prévenir. */
    @Size(max = 150)
    private String personneAPrevenir;
    @Pattern(regexp = "^\\+?[0-9 ]{8,20}$", message = "Numéro de la personne à prévenir invalide")
    private String personneAPrevenirTelephone;
    @Size(max = 50)
    private String nationalite;
    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Groupe sanguin attendu : A+, A-, B+, B-, AB+, AB-, O+ ou O-")
    private String groupeSanguin;
    @DecimalMin("0.5") @DecimalMax("2.5")
    private Double taille;
    @DecimalMin("20") @DecimalMax("250")
    private Double poids;
    @Size(max = 50)
    private String dernierDiplome;
    /** Diplômes obtenus ; le plus élevé devient le dernier diplôme. */
    private java.util.List<@Size(max = 50) String> diplomes;
    @Pattern(regexp = "^(FONCTIONNAIRE|NON_FONCTIONNAIRE)$", message = "Catégorie attendue : FONCTIONNAIRE ou NON_FONCTIONNAIRE")
    private String categorie;
    @Size(max = 50)
    private String compagnie;
    @Size(max = 50)
    private String section;
    @Size(max = 50)
    private String brigade;
    private java.time.LocalDate dateIncorporation;
    private Boolean disponibleMobilisation;
    private StatutMembreEnum statut;
    @NotNull(message = "La cohorte est obligatoire")
    private Long cohorteId;
}
