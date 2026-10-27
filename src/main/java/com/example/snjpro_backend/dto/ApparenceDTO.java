package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.LogoApplication;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Apparence de l'application : logo affiché et charte graphique associée. */
@Data @NoArgsConstructor @AllArgsConstructor
public class ApparenceDTO {
    @NotNull(message = "Le logo est obligatoire")
    private LogoApplication logo;
}
