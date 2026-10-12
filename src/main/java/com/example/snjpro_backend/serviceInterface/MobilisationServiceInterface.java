package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.MobilisationDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MobilisationServiceInterface {
    // Gestion (web)
    List<Resume> lister(StatutMobilisation statut, String recherche);
    Statistiques statistiques();
    Detail creer(Informations dto);
    Detail modifierInformations(Long id, Informations dto);
    Detail definirCriteres(Long id, Criteres dto);
    Apercu apercu(Long id, int limite);
    Detail envoyer(Long id, Envoi dto);
    Detail obtenir(Long id);
    Page<Participant> participants(Long id, ReponseMobilisation reponse, Pageable pageable);
    byte[] exporterParticipants(Long id, ReponseMobilisation reponse, FormatFichier format);
    Detail relancer(Long id);
    Detail cloturer(Long id, Cloture dto);
    void supprimer(Long id);

    // Espace membre (mobile)
    List<MaMobilisation> mesMobilisations();
    MaMobilisation maMobilisation(Long id);
    MaMobilisation repondre(Long id, ReponseMembre dto);
}
