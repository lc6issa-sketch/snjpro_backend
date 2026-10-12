package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DocumentDTOs.*;
import com.example.snjpro_backend.entity.Document;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentServiceInterface {
    // Bibliothèque de documents du SNJ
    List<DossierDTO> dossiers();
    DossierDTO creerDossier(DossierEdition dto);
    DossierDTO modifierDossier(Long id, DossierEdition dto);
    void supprimerDossier(Long id);
    List<DocumentDTO> lister(Long dossierId, VisibiliteDocument visibilite, String recherche);
    DocumentDTO televerser(MultipartFile fichier, String titre, Long dossierId, VisibiliteDocument visibilite);
    DocumentDTO modifier(Long id, DocumentEdition dto);
    void supprimer(Long id);
    Fichier telecharger(Long id);
    Espace espace();

    // Documents d'un membre (gestion)
    DocumentsMembre documentsMembre(Long membreId);
    DocumentDTO televerserPourMembre(Long membreId, MultipartFile fichier, TypeDocument type, String titre);
    DocumentDTO verifier(Long documentId, boolean verifie);
    DocumentDTO genererAttestation(Long membreId);

    // Espace membre
    MesDocuments mesDocuments();
    DocumentDTO televerserMonDocument(MultipartFile fichier, TypeDocument type, String titre);
    Fichier telechargerMonDocument(Long id);

    /** Enregistre une pièce jointe liée à un membre (demande, message…) et la retourne. */
    Document enregistrerPieceMembre(Membre membre, MultipartFile fichier, TypeDocument type, String titre);
}
