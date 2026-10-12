package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.CompetenceDTO;
import com.example.snjpro_backend.dto.ParcoursSNJDTO;
import java.util.List;

public interface CompetenceParcoursSNJServiceInterface {
    CompetenceDTO ajouterCompetence(Long membreId, CompetenceDTO dto);
    List<CompetenceDTO> obtenirCompetencesMembre(Long membreId);
    CompetenceDTO modifierCompetence(Long membreId, Long id, CompetenceDTO dto);
    void supprimerCompetence(Long membreId, Long id);
    ParcoursSNJDTO ajouterParcours(Long membreId, ParcoursSNJDTO dto);
    List<ParcoursSNJDTO> obtenirParcoursMembre(Long membreId);
    ParcoursSNJDTO modifierParcours(Long membreId, Long id, ParcoursSNJDTO dto);
    void supprimerParcours(Long membreId, Long id);
    List<String> obtenirIntitulesCompetences();
}
