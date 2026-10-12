package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.CompetenceReferentielDTOs.*;

import java.util.List;

public interface CompetenceReferentielServiceInterface {
    /** @param tri MEMBRES (plus représentées), NOM ou RECENTES */
    List<Competence> lister(String categorie, String recherche, String tri);
    List<Categorie> categories();
    Competence obtenir(Long id);
    Competence creer(Edition dto);
    Competence modifier(Long id, Edition dto);
    void supprimer(Long id);
    List<Detenteur> detenteurs(Long id);
    /** Compétences populaires que le membre connecté ne possède pas encore. */
    List<Competence> suggestionsPourMoi(int limite);
    /** Ajoute au référentiel les intitulés saisis sur les fiches des membres qui n'y figurent pas encore. */
    int synchroniser();
}
