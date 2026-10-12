package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.NotificationDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutNotification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationServiceInterface {
    private static final String ENTITE = "NOTIFICATION";

    private final NotificationRepository notificationRepository;
    private final NotificationDestinataireRepository destinataireRepository;
    private final AlerteUtilisateurRepository alerteRepository;
    private final UserRepository userRepository;
    private final CohorteRepository cohorteRepository;
    private final SelectionMembres selection;
    private final EnvoiMessageService envoi;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Envoi aux membres
    // =====================================================================

    @Override
    public Resume envoyer(Envoi dto) {
        Notification n = new Notification();
        appliquer(n, dto);
        n.setEmetteur(perimetre.utilisateurCourant().getUsername());
        if (dto.isBrouillon()) {
            n.setStatut(StatutNotification.BROUILLON);
        } else if (dto.getProgrammeLe() != null && dto.getProgrammeLe().isAfter(LocalDateTime.now())) {
            n.setStatut(StatutNotification.PROGRAMMEE);
        } else {
            n.setStatut(StatutNotification.BROUILLON);
            n = notificationRepository.save(n);
            diffuser(n);
        }
        n = notificationRepository.save(n);
        historiqueService.enregistrer(n.getStatut() == StatutNotification.ENVOYEE ? "ENVOI" : "CREATION", ENTITE, n.getId(),
                n.getTitre() + " · " + libelleDestinataires(n));
        return toResume(n);
    }

    @Override
    public Resume modifierBrouillon(Long id, Envoi dto) {
        Notification n = trouver(id);
        if (n.getStatut() == StatutNotification.ENVOYEE)
            throw new ConflitException("Une notification déjà envoyée ne peut plus être modifiée.");
        appliquer(n, dto);
        if (dto.getProgrammeLe() != null && dto.getProgrammeLe().isAfter(LocalDateTime.now()) && !dto.isBrouillon())
            n.setStatut(StatutNotification.PROGRAMMEE);
        return toResume(notificationRepository.save(n));
    }

    @Override
    public Resume envoyerBrouillon(Long id) {
        Notification n = trouver(id);
        if (n.getStatut() == StatutNotification.ENVOYEE)
            throw new ConflitException("Cette notification a déjà été envoyée.");
        diffuser(n);
        historiqueService.enregistrer("ENVOI", ENTITE, id, n.getTitre());
        return toResume(notificationRepository.save(n));
    }

    @Override
    public void supprimer(Long id) {
        Notification n = trouver(id);
        if (n.getStatut() == StatutNotification.ENVOYEE)
            throw new ConflitException("Une notification envoyée ne peut pas être supprimée (traçabilité).");
        notificationRepository.delete(n);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, n.getTitre());
    }

    @Override @Transactional(readOnly = true)
    public Page<Resume> historique(Pageable pageable) {
        return notificationRepository.findAllByOrderByDateCreationDesc(pageable).map(this::toResume);
    }

    @Override @Transactional(readOnly = true)
    public Resume obtenir(Long id) { return toResume(trouver(id)); }

    @Override @Transactional(readOnly = true)
    public Statistiques statistiques() {
        LocalDateTime debutMois = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        List<Notification> duMois = notificationRepository.findByDateEnvoiAfter(debutMois);
        long destinataires = 0, lus = 0, sms = 0;
        for (Notification n : duMois) {
            destinataires += destinataireRepository.countByNotificationId(n.getId());
            lus += destinataireRepository.countByNotificationIdAndLuTrue(n.getId());
            sms += n.getNombreSms() == null ? 0 : n.getNombreSms();
        }
        long programmees = notificationRepository.findAll().stream().filter(n -> n.getStatut() == StatutNotification.PROGRAMMEE).count();
        return Statistiques.builder()
                .envoyeesCeMois(duMois.size())
                .tauxLectureMoyen(destinataires == 0 ? 0 : (int) Math.round(lus * 100.0 / destinataires))
                .smsEnvoyesCeMois(sms)
                .programmees(programmees)
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Estimation estimer(CibleNotification cible, List<String> valeurs, boolean sms) {
        List<Membre> membres = selection.parCible(cible, restreindreValeurs(cible, valeurs));
        int nbSms = sms ? (int) membres.stream().filter(m -> StringUtils.hasText(m.getNumeroTelephone())).count() : 0;
        return Estimation.builder().nombreDestinataires(membres.size()).nombreSms(nbSms).build();
    }

    @Override
    public void notifierMembres(Collection<Membre> membres, TypeNotification type, String titre, String message,
                                boolean sms, String lienType, Long lienId) {
        if (membres == null || membres.isEmpty()) return;
        Notification n = Notification.builder()
                .type(type).titre(titre).message(message)
                .cible(CibleNotification.SELECTION)
                .cibleValeurs(String.join(",", membres.stream().map(m -> m.getId().toString()).toList()))
                .canalApplication(true).canalSms(sms)
                .statut(StatutNotification.BROUILLON)
                .lienType(lienType).lienId(lienId)
                .emetteur(emetteurCourant())
                .build();
        n = notificationRepository.save(n);
        distribuer(n, membres);
        notificationRepository.save(n);
    }

    /** Envoie les notifications programmées arrivées à échéance. */
    @Scheduled(fixedDelay = 60_000)
    public void envoyerProgrammees() {
        for (Notification n : notificationRepository.findByStatutAndProgrammeLeLessThanEqual(StatutNotification.PROGRAMMEE, LocalDateTime.now())) {
            try {
                diffuser(n);
                notificationRepository.save(n);
                log.info("Notification programmée {} envoyée à {} membre(s)", n.getId(), n.getNombreDestinataires());
            } catch (Exception e) {
                log.error("Échec de l'envoi programmé de la notification {} : {}", n.getId(), e.getMessage());
            }
        }
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public Page<MaNotification> mesNotifications(boolean nonLues, TypeNotification type, Pageable pageable) {
        return destinataireRepository.pourMembre(perimetre.membreIdCourant(), nonLues, type, pageable).map(this::toMaNotification);
    }

    @Override @Transactional(readOnly = true)
    public long compterNonLues() {
        return destinataireRepository.countByMembreIdAndLuFalse(perimetre.membreIdCourant());
    }

    @Override
    public MaNotification marquerLue(Long id) {
        NotificationDestinataire d = destinataireRepository.findByIdAndMembreId(id, perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Notification non trouvée : " + id));
        if (!d.isLu()) {
            d.setLu(true);
            d.setDateLecture(LocalDateTime.now());
        }
        return toMaNotification(destinataireRepository.save(d));
    }

    @Override
    public int toutMarquerLu() {
        return destinataireRepository.toutMarquerLu(perimetre.membreIdCourant(), LocalDateTime.now());
    }

    // =====================================================================
    // Alertes du panneau web
    // =====================================================================

    @Override
    public void alerterRoles(Collection<RoleEnum> roles, String region, String titre, String message, String type,
                             String lienType, Long lienId) {
        userRepository.findAll().stream()
                .filter(u -> u.isActif() && roles.contains(u.getRole()))
                .filter(u -> u.getRole() != RoleEnum.ROLE_RESPONSABLE_REGIONAL || region == null
                        || region.equalsIgnoreCase(u.getRegion()))
                .forEach(u -> alerterUtilisateur(u.getId(), titre, message, type, lienType, lienId));
    }

    @Override
    public void alerterUtilisateur(Long userId, String titre, String message, String type, String lienType, Long lienId) {
        alerteRepository.save(AlerteUtilisateur.builder()
                .userId(userId).titre(titre).message(message).type(type)
                .lienType(lienType).lienId(lienId).build());
    }

    @Override @Transactional(readOnly = true)
    public Page<Alerte> mesAlertes(boolean nonLues, Pageable pageable) {
        Long userId = perimetre.utilisateurCourant().getId();
        Page<AlerteUtilisateur> page = nonLues
                ? alerteRepository.findByUserIdAndLuFalseOrderByDateCreationDesc(userId, pageable)
                : alerteRepository.findByUserIdOrderByDateCreationDesc(userId, pageable);
        return page.map(this::toAlerte);
    }

    @Override @Transactional(readOnly = true)
    public long compterAlertesNonLues() {
        return alerteRepository.countByUserIdAndLuFalse(perimetre.utilisateurCourant().getId());
    }

    @Override
    public Alerte marquerAlerteLue(Long id) {
        AlerteUtilisateur a = alerteRepository.findByIdAndUserId(id, perimetre.utilisateurCourant().getId())
                .orElseThrow(() -> new EntityNotFoundException("Alerte non trouvée : " + id));
        a.setLu(true);
        return toAlerte(alerteRepository.save(a));
    }

    @Override
    public int toutesAlertesLues() {
        return alerteRepository.toutMarquerLu(perimetre.utilisateurCourant().getId());
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void appliquer(Notification n, Envoi dto) {
        if (!dto.isCanalApplication() && !dto.isCanalSms() && !dto.isCanalEmail())
            throw new IllegalArgumentException("Choisissez au moins un canal d'envoi.");
        if (dto.getCible() != CibleNotification.TOUS && (dto.getValeurs() == null || dto.getValeurs().isEmpty()))
            throw new IllegalArgumentException("Précisez les destinataires (régions, cohortes ou membres).");
        List<String> valeurs = restreindreValeurs(dto.getCible(), dto.getValeurs());
        n.setType(dto.getType());
        n.setTitre(dto.getTitre().trim());
        n.setMessage(dto.getMessage().trim());
        n.setCible(perimetreCible(dto.getCible()));
        n.setCibleValeurs(String.join(",", valeurs));
        n.setCanalApplication(dto.isCanalApplication());
        n.setCanalSms(dto.isCanalSms());
        n.setCanalEmail(dto.isCanalEmail());
        n.setProgrammeLe(dto.getProgrammeLe());
    }

    /** Un responsable régional ou de cohorte n'écrit qu'aux membres de son périmètre. */
    private CibleNotification perimetreCible(CibleNotification cible) {
        return switch (perimetre.utilisateurCourant().getRole()) {
            case ROLE_RESPONSABLE_REGIONAL -> cible == CibleNotification.SELECTION ? cible : CibleNotification.REGION;
            case ROLE_RESPONSABLE_COHORTE -> cible == CibleNotification.SELECTION ? cible : CibleNotification.COHORTE;
            default -> cible;
        };
    }

    private List<String> restreindreValeurs(CibleNotification cible, List<String> valeurs) {
        User u = perimetre.utilisateurCourant();
        if (cible != CibleNotification.SELECTION) {
            if (u.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL) return List.of(u.getRegion());
            if (u.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE) return List.of(String.valueOf(u.getCohorteId()));
        }
        return valeurs == null ? List.of() : valeurs.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private void diffuser(Notification n) {
        distribuer(n, selection.parCible(n.getCible(), valeurs(n)));
    }

    private void distribuer(Notification n, Collection<Membre> membres) {
        int sms = 0;
        for (Membre m : membres) {
            destinataireRepository.save(NotificationDestinataire.builder().notification(n).membre(m).build());
            if (n.isCanalSms() && envoi.sms(m.getNumeroTelephone(), n.getTitre() + " — " + n.getMessage())) sms++;
            if (n.isCanalEmail()) envoi.email(m.getEmail(), n.getTitre(), n.getMessage());
        }
        n.setNombreDestinataires(membres.size());
        n.setNombreSms(sms);
        n.setDateEnvoi(LocalDateTime.now());
        n.setStatut(StatutNotification.ENVOYEE);
    }

    private List<String> valeurs(Notification n) {
        return StringUtils.hasText(n.getCibleValeurs()) ? Arrays.asList(n.getCibleValeurs().split(",")) : List.of();
    }

    private String libelleDestinataires(Notification n) {
        List<String> v = valeurs(n);
        return switch (n.getCible()) {
            case TOUS -> "Tous les membres";
            case REGION -> "Région : " + String.join(", ", v);
            case COHORTE -> "Cohorte : " + String.join(", ", v.stream().map(id -> cohorteRepository.findById(Long.valueOf(id))
                    .map(c -> c.getNom()).orElse(id)).toList());
            case SELECTION -> v.size() + " membre(s) sélectionné(s)";
        };
    }

    private Notification trouver(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification non trouvée : " + id));
    }

    private String emetteurCourant() {
        try {
            return perimetre.utilisateurCourant().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    private Resume toResume(Notification n) {
        long lus = n.getId() == null ? 0 : destinataireRepository.countByNotificationIdAndLuTrue(n.getId());
        int total = n.getNombreDestinataires() == null ? 0 : n.getNombreDestinataires();
        List<String> canaux = new ArrayList<>();
        if (n.isCanalApplication()) canaux.add("APPLICATION");
        if (n.isCanalSms()) canaux.add("SMS");
        if (n.isCanalEmail()) canaux.add("EMAIL");
        return Resume.builder()
                .id(n.getId()).titre(n.getTitre()).message(n.getMessage()).type(n.getType())
                .cible(n.getCible()).valeurs(valeurs(n)).destinatairesLibelle(libelleDestinataires(n))
                .canaux(canaux).statut(n.getStatut()).programmeLe(n.getProgrammeLe()).dateEnvoi(n.getDateEnvoi())
                .nombreDestinataires(total).nombreLus(lus)
                .tauxLecture(total == 0 ? 0 : (int) Math.round(lus * 100.0 / total))
                .emetteur(n.getEmetteur())
                .build();
    }

    private MaNotification toMaNotification(NotificationDestinataire d) {
        Notification n = d.getNotification();
        return MaNotification.builder()
                .id(d.getId()).titre(n.getTitre()).message(n.getMessage()).type(n.getType())
                .lu(d.isLu()).date(n.getDateEnvoi()).lienType(n.getLienType()).lienId(n.getLienId())
                .build();
    }

    private Alerte toAlerte(AlerteUtilisateur a) {
        return Alerte.builder()
                .id(a.getId()).titre(a.getTitre()).message(a.getMessage()).type(a.getType())
                .lienType(a.getLienType()).lienId(a.getLienId()).lu(a.isLu()).date(a.getDateCreation())
                .build();
    }
}
