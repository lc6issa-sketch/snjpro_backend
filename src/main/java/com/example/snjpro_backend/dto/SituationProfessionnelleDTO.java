package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class SituationProfessionnelleDTO {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Déduit de l'URL")
    private Long membreId;
    @NotNull(message = "Le statut professionnel est obligatoire")
    private StatutProfessionnelEnum statut;
    @Size(max = 100)
    private String profession;
    @Size(max = 100)
    private String secteurActivite;
    @Size(max = 150)
    private String nomEmployeur;
    private String description;
    private LocalDate dateDebutActivite;
}
