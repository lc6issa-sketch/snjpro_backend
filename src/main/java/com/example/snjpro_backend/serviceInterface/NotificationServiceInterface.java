package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.NotificationDTOs.*;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.enumeration.RoleEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface NotificationServiceInterface {
    // Envoi aux membres (web)
    Resume envoyer(Envoi dto);
    Resume modifierBrouillon(Long id, Envoi dto);
    Resume envoyerBrouillon(Long id);
    void supprimer(Long id);
    Page<Resume> historique(Pageable pageable);
    Resume obtenir(Long id);
    Statistiques statistiques();
    Estimation estimer(CibleNotification cible, List<String> valeurs, boolean sms);

    /** Notification automatique (mobilisation, actualité, demande…) à une liste de membres. */
    void notifierMembres(Collection<Membre> membres, TypeNotification type, String titre, String message,
                         boolean sms, String lienType, Long lienId);

    // Espace membre (mobile)
    Page<MaNotification> mesNotifications(boolean nonLues, TypeNotification type, Pageable pageable);
    long compterNonLues();
    MaNotification marquerLue(Long id);
    int toutMarquerLu();

    // Alertes du panneau web
    void alerterRoles(Collection<RoleEnum> roles, String region, String titre, String message, String type,
                      String lienType, Long lienId);
    /** Alerte les responsables de la cohorte indiquée (et uniquement eux). */
    void alerterResponsablesCohorte(Long cohorteId, String titre, String message, String type, String lienType, Long lienId);
    void alerterUtilisateur(Long userId, String titre, String message, String type, String lienType, Long lienId);
    Page<Alerte> mesAlertes(boolean nonLues, Pageable pageable);
    long compterAlertesNonLues();
    Alerte marquerAlerteLue(Long id);
    int toutesAlertesLues();
}
