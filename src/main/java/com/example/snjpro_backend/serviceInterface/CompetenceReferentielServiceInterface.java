package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.CompetenceReferentielDTOs.*;

import java.util.List;

public interface CompetenceReferentielServiceInterface {
    /** @param tri MEMBRES (plus représentées), NOM ou RECENTES */
    /** toutes = false : un responsable de cohorte ne voit que les compétences détenues par les membres de sa cohorte. */
    List<Competence> lister(String categorie, String recherche, String tri, boolean toutes);
    List<Categorie> categories(boolean toutes);
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
