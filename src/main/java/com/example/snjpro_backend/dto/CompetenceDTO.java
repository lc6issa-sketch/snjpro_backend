package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.NiveauCompetenceEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompetenceDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Déduit de l'URL")
    private Long membreId;
    @NotBlank(message = "L'intitulé de la compétence est obligatoire")
    @Size(max = 100)
    private String intitule;
    @Size(max = 100)
    private String categorie;
    private NiveauCompetenceEnum niveau;
    private String description;
}
