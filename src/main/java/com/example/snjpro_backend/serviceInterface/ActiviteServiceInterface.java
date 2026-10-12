package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.ActiviteDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActivite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ActiviteServiceInterface {
    // Gestion (web)
    List<Resume> lister(StatutActivite statut, Integer annee, Integer mois);
    Resume obtenir(Long id);
    Resume creer(Edition dto);
    Resume modifier(Long id, Edition dto);
    Resume terminer(Long id);
    void supprimer(Long id);
    CodeQr codeCourant(Long id);
    Projection projection(Long id);
    Page<PresenceDTO> presences(Long id, Pageable pageable);
    PresenceDTO saisirPresence(Long id, SaisieManuelle dto);
    void supprimerPresence(Long id, Long presenceId);
    byte[] exporterPresences(Long id, FormatFichier format);

    // Espace membre (mobile)
    List<Resume> activitesDuJour();
    ConfirmationPresence pointer(Pointage dto);
    List<PresenceDTO> mesPresences();
}
