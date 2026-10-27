package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.SouvenirDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutSouvenir;
import com.example.snjpro_backend.enumeration.Enumerations.TypeMedia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SouvenirServiceInterface {
    // Gestion (web)
    Page<Resume> lister(Long cohorteId, StatutSouvenir statut, TypeMedia type, String recherche, Pageable pageable);
    Statistiques statistiques(Long cohorteId);
    Resume obtenir(Long id);
    Resume creer(Edition dto, List<MultipartFile> fichiers);
    Resume modifier(Long id, Edition dto);
    Resume ajouterMedias(Long id, List<MultipartFile> fichiers);
    Resume supprimerMedia(Long id, Long mediaId);
    Resume valider(Long id);
    Resume rejeter(Long id, String motif);
    void supprimer(Long id);
    Fichier fichier(Long mediaId);
    /** Image de couverture d'une vidéo ou d'un son (remplace l'éventuelle couverture existante). */
    Resume definirCouverture(Long id, Long mediaId, MultipartFile image);
    Resume supprimerCouverture(Long id, Long mediaId);
    Fichier couverture(Long mediaId);

    // Espace membre (application mobile)
    List<Resume> filMembre();
    List<Resume> mesEnvois();
    Resume soumettre(Edition dto, List<MultipartFile> fichiers);
    void supprimerMonEnvoi(Long id);
    /** Couverture d'un de mes envois, tant qu'il n'est pas publié. */
    Resume definirCouvertureMonEnvoi(Long id, Long mediaId, MultipartFile image);
}
