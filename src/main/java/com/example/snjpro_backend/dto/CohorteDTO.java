package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.enumeration.TypeCohorteEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CohorteDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @NotBlank(message = "Le nom de la cohorte est obligatoire")
    @Size(max = 100)
    private String nom;
    @Size(max = 20)
    private String code;
    private String description;
    private Integer anneeDebut;
    private Integer anneeFin;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    @Schema(description = "EN_FORMATION, INCORPOREE ou EN_FONCTION (ARCHIVEE uniquement via l'action d'archivage)")
    private StatutCohorteEnum statut;
    @NotNull(message = "Précisez s'il s'agit d'une cohorte de fonctionnaires ou de non-fonctionnaires")
    @Schema(description = "FONCTIONNAIRE ou NON_FONCTIONNAIRE")
    private TypeCohorteEnum typeCohorte;
    @Size(max = 150)
    @Schema(description = "Directeur du centre de formation")
    private String directeurCentre;
    @Size(max = 150)
    @Schema(description = "Major du centre de formation")
    private String majorCentre;
    private String region;
    private String responsable;
    @Schema(description = "Numéro d'ordre de la promotion (ex. 22)")
    private Integer numero;
    @Size(max = 100)
    private String centre;
    private Integer recruesInitiales;
    private Integer nombreCompagnies;
    private Integer nombreSections;
    @Schema(description = "Compte du responsable de cohorte (utilisateur ROLE_RESPONSABLE_COHORTE)")
    private Long responsableId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String responsableNom;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long membresActifs;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Part des profils complets (%)")
    private Integer pourcentageProfilsAJour;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long nombreMembres;
}
