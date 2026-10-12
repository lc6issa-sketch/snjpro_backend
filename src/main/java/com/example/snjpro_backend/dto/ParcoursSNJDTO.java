package com.example.snjpro_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ParcoursSNJDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Déduit de l'URL")
    private Long membreId;
    @NotBlank(message = "L'intitulé du parcours est obligatoire")
    @Size(max = 150)
    private String intitule;
    @Schema(description = "Ex : FORMATION, AFFECTATION, MISSION, DISTINCTION, SANCTION")
    @Size(max = 100)
    private String type;
    private String description;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    @Size(max = 100)
    private String lieu;
}
