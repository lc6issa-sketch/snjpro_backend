package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.ActualiteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.CategorieActualite;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ActualiteServiceInterface {
    // Gestion (web)
    List<Resume> lister(StatutActualite statut, CategorieActualite categorie, String recherche);
    Compteurs compteurs();
    Detail obtenir(Long id);
    Detail creer(Edition dto);
    Detail modifier(Long id, Edition dto);
    Detail publier(Long id);
    Detail archiver(Long id);
    void supprimer(Long id);
    Detail changerImage(Long id, MultipartFile image);
    byte[] image(Long id);
    List<Inscrit> inscrits(Long id);

    // Espace membre (mobile)
    List<Resume> fil(CategorieActualite categorie);
    Resume aLaUne();
    Detail lire(Long id);
    Detail inscrire(Long id);
    Detail desinscrire(Long id);
}
