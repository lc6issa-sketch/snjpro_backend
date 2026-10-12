package com.example.snjpro_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LocalisationDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Déduit de l'URL")
    private Long membreId;
    @Schema(description = "Mali par défaut")
    @Size(max = 100)
    private String pays;
    @Schema(description = "Voir GET /api/referentiels/regions-mali")
    @Size(max = 100)
    private String region;
    @Size(max = 100)
    private String cercle;
    @Size(max = 100)
    private String commune;
    @Size(max = 100)
    private String quartier;
    private String adresseComplete;
}
