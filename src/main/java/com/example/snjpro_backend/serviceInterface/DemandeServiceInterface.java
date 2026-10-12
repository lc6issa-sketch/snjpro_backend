package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DemandeDTOs.*;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.enumeration.Enumerations.StatutDemande;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDemande;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DemandeServiceInterface {
    // Traitement (web)
    List<Resume> lister(StatutDemande statut, TypeDemande type, String recherche);
    Statistiques statistiques();
    Detail obtenir(Long id);
    Detail prendreEnCharge(Long id);
    Detail valider(Long id, Validation dto);
    Detail rejeter(Long id, Rejet dto);
    Detail demanderComplement(Long id, Complement dto);
    Fichier pieceJointe(Long id);

    // Espace membre (mobile)
    List<Resume> mesDemandes(Boolean traitees);
    Detail maDemande(Long id);
    Detail creer(Nouvelle dto, MultipartFile fichier);
    Detail completer(Long id, String message, MultipartFile fichier);
    void annuler(Long id);
}
