package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.SituationProfessionnelleDTO;

public interface SituationProfessionnelleServiceInterface {
    SituationProfessionnelleDTO enregistrer(Long membreId, SituationProfessionnelleDTO dto);
    SituationProfessionnelleDTO modifier(Long membreId, SituationProfessionnelleDTO dto);
    SituationProfessionnelleDTO obtenir(Long membreId);
}
