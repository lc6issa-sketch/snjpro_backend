package com.example.snjpro_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Profil numérique complet d'un membre (F07) : identité, situation, localisation, compétences et parcours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilMembreDTO {
    private MembreResponseDTO membre;
    private List<CompetenceDTO> competences;
    private List<ParcoursSNJDTO> parcours;
    /** Pourcentage de complétude du profil (KPI « profils complets »). */
    private int tauxCompletude;
}
