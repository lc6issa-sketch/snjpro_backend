package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.OpportuniteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutCandidature;
import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import com.example.snjpro_backend.enumeration.Enumerations.TypeOpportunite;

import java.util.List;
import java.util.Map;

public interface OpportuniteServiceInterface {
    // Gestion (web)
    List<Resume> lister(TypeOpportunite type, StatutOpportunite statut, String recherche);
    Compteurs compteurs();
    Detail obtenir(Long id);
    Detail creer(Edition dto);
    Detail modifier(Long id, Edition dto);
    Detail publier(Long id);
    Detail cloturer(Long id);
    void supprimer(Long id);
    List<CandidatureDTO> candidatures(Long id);
    CandidatureDTO changerStatutCandidature(Long candidatureId, StatutCandidature statut);
    Map<String, Object> estimerCorrespondances(List<String> competences, String region);

    // Espace membre (mobile)
    List<Resume> pourMoi(TypeOpportunite type, boolean recommandeesUniquement);
    Detail consulter(Long id);
    CandidatureDTO postuler(Long id, Postuler dto);
    Resume basculerFavori(Long id);
    List<CandidatureDTO> mesCandidatures();
}
