package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.LocalisationDTO;
import java.util.List;

public interface LocalisationServiceInterface {
    LocalisationDTO enregistrer(Long membreId, LocalisationDTO dto);
    LocalisationDTO modifier(Long membreId, LocalisationDTO dto);
    LocalisationDTO obtenir(Long membreId);
    List<String> obtenirToutesLesRegions();
}
