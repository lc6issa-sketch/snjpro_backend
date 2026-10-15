package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.ActiviteDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.*;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.ActiviteServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ActiviteServiceImpl implements ActiviteServiceInterface {
    private static final String ENTITE = "ACTIVITE";
    private static final int PERIODE_SECONDES = 30;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O ni 1/I
    private static final String PREFIXE_QR = "SNJPRO:ACT:";
    /** Délai maximal pour envoyer une présence scannée hors connexion (zone sans réseau). */
    private static final long DELAI_HORS_LIGNE_HEURES = 72;

    private final ActiviteRepository activiteRepository;
    private final PresenceRepository presenceRepository;
    private final MobilisationRepository mobilisationRepository;
    private final ParticipationMobilisationRepository participationRepository;
    private final MembreRepository membreRepository;
    private final ParcoursSNJRepository parcoursRepository;
    private final GenerateurFichiers generateur;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Gestion
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> lister(StatutActivite statut, Integer annee, Integer mois) {
        User u = perimetre.utilisateurCourant();
        return activiteRepository.findAllByOrderByDateDesc().stream()
                .filter(a -> gerable(a, u))
                .filter(a -> statut == null || statut(a) == statut)
                .filter(a -> annee == null || a.getDate().getYear() == annee)
                .filter(a -> mois == null || a.getDate().getMonthValue() == mois)
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Resume obtenir(Long id) { return toResume(trouverGerable(id)); }

    @Override
    public Resume creer(Edition dto) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Activite a = Activite.builder().secretQr(HexFormat.of().formatHex(secret))
                .creePar(perimetre.utilisateurCourant().getUsername()).build();
        appliquer(a, dto);
        a = activiteRepository.save(a);
        historiqueService.enregistrer("CREATION", ENTITE, a.getId(), a.getTitre());
        return toResume(a);
    }

    @Override
    public Resume modifier(Long id, Edition dto) {
        Activite a = trouverGerable(id);
        appliquer(a, dto);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, a.getTitre());
        return toResume(activiteRepository.save(a));
    }

    @Override
    public Resume terminer(Long id) {
        Activite a = trouverGerable(id);
        a.setStatut(StatutActivite.TERMINEE);
        a.setQrActif(false);
        historiqueService.enregistrer("CLOTURE", ENTITE, id, a.getTitre());
        return toResume(activiteRepository.save(a));
    }

    @Override
    public void supprimer(Long id) {
        Activite a = trouverGerable(id);
        if (presenceRepository.countByActiviteId(id) > 0)
            throw new ConflitException("Des présences sont enregistrées : terminez l'activité plutôt que de la supprimer.");
        activiteRepository.delete(a);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, a.getTitre());
    }

    @Override @Transactional(readOnly = true)
    public CodeQr codeCourant(Long id) {
        Activite a = trouverGerable(id);
        if (!a.isQrActif() || statut(a) == StatutActivite.TERMINEE)
            throw new ConflitException("Le QR code de présence n'est pas actif pour cette activité.");
        long maintenant = Instant.now().getEpochSecond();
        long fenetre = maintenant / PERIODE_SECONDES;
        String code = code(a, fenetre);
        int restantes = (int) ((fenetre + 1) * PERIODE_SECONDES - maintenant);
        return CodeQr.builder().activiteId(id).code(code).contenuQr(PREFIXE_QR + id + ":" + code)
                .secondesRestantes(restantes).valableJusqua(LocalTime.now().plusSeconds(restantes).withNano(0)).build();
    }

    @Override @Transactional(readOnly = true)
    public Projection projection(Long id) {
        return Projection.builder()
                .activite(toResume(trouverGerable(id)))
                .codeQr(codeCourant(id))
                .dernieresArrivees(presenceRepository.findByActiviteIdOrderByDateHeureDesc(id, PageRequest.of(0, 5))
                        .map(this::toPresence).getContent())
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Page<PresenceDTO> presences(Long id, Pageable pageable) {
        trouverGerable(id);
        return presenceRepository.findByActiviteIdOrderByDateHeureDesc(id, pageable).map(this::toPresence);
    }

    @Override
    public PresenceDTO saisirPresence(Long id, SaisieManuelle dto) {
        Activite a = trouverGerable(id);
        Membre m = membreRepository.findById(dto.getMembreId())
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + dto.getMembreId()));
        if (!perimetre.couvre(m))
            throw new AccessDeniedException("Ce membre ne fait pas partie de votre périmètre.");
        if (presenceRepository.findByActiviteIdAndMembreId(id, m.getId()).isPresent())
            throw new ConflitException(m.getPrenom() + " " + m.getNom() + " est déjà enregistré(e) comme présent(e).");
        LocalDateTime heure = dto.getHeureArrivee() != null ? LocalDateTime.of(LocalDate.now(), dto.getHeureArrivee()) : LocalDateTime.now();
        Presence p = enregistrer(a, m, ModePresence.MANUEL, heure, dto.getMotif(), perimetre.utilisateurCourant().getUsername());
        historiqueService.enregistrer("PRESENCE_MANUELLE", ENTITE, id, m.getIdentifiantSnj() + (dto.getMotif() != null ? " · " + dto.getMotif() : ""));
        return toPresence(p);
    }

    @Override
    public void supprimerPresence(Long id, Long presenceId) {
        Presence p = presenceRepository.findById(presenceId)
                .filter(x -> x.getActivite().getId().equals(id))
                .orElseThrow(() -> new EntityNotFoundException("Présence non trouvée : " + presenceId));
        presenceRepository.delete(p);
        historiqueService.enregistrer("SUPPRESSION_PRESENCE", ENTITE, id, p.getMembre().getIdentifiantSnj());
    }

    @Override @Transactional(readOnly = true)
    public byte[] exporterPresences(Long id, FormatFichier format) {
        Activite a = trouverGerable(id);
        List<List<String>> lignes = presenceRepository.findByActiviteIdOrderByDateHeureDesc(id).stream()
                .map(this::toPresence)
                .map(p -> List.of(p.getIdentifiantSnj(), p.getPrenom(), p.getNom(), p.getDateHeure().toString(),
                        p.getMode().name(), p.getMotif() == null ? "" : p.getMotif()))
                .toList();
        historiqueService.enregistrer("EXPORT", ENTITE, id, lignes.size() + " présence(s) · " + format);
        return generateur.tableau(new GenerateurFichiers.Tableau("Présences - " + a.getTitre(),
                List.of("Identifiant", "Prénom", "Nom", "Arrivée", "Mode", "Motif"), lignes), format);
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> activitesDuJour() {
        Membre m = membreCourant();
        String region = m.getLocalisation() != null ? m.getLocalisation().getRegion() : null;
        return activiteRepository.findAllByOrderByDateDesc().stream()
                .filter(a -> statut(a) == StatutActivite.EN_COURS)
                .filter(a -> a.getCohorteId() == null || a.getCohorteId().equals(m.getCohorte().getId()))
                .filter(a -> a.getRegion() == null || a.getRegion().equalsIgnoreCase(region)
                        || (a.getMobilisation() != null && participationRepository
                        .findByMobilisationIdAndMembreId(a.getMobilisation().getId(), m.getId()).isPresent()))
                .map(this::toResume)
                .toList();
    }

    @Override
    public ConfirmationPresence pointer(Pointage dto) {
        Membre m = membreCourant();
        // hors connexion : le code est vérifié pour l'heure du scan, pas pour l'heure d'envoi
        boolean horsLigne = dto.getScanneLe() != null;
        Instant moment = horsLigne ? momentDuScan(dto.getScanneLe()) : Instant.now();
        LocalDate jour = LocalDate.ofInstant(moment, ZoneId.systemDefault());
        Activite a;
        String code;
        ModePresence mode;
        if (StringUtils.hasText(dto.getQr())) {
            String qr = dto.getQr().trim();
            if (!qr.startsWith(PREFIXE_QR)) throw new IllegalArgumentException("Ce QR code n'est pas un QR code de présence SNJPro.");
            String[] parties = qr.substring(PREFIXE_QR.length()).split(":");
            if (parties.length != 2) throw new IllegalArgumentException("QR code illisible.");
            a = trouver(Long.valueOf(parties[0]));
            code = parties[1];
            mode = ModePresence.QR_CODE;
            if (!codeValide(a, code, moment))
                throw new IllegalArgumentException(horsLigne
                        ? "Ce QR code n'était pas valable à l'heure du scan."
                        : "QR code expiré : scannez le code actuellement affiché.");
        } else if (StringUtils.hasText(dto.getCode())) {
            code = dto.getCode().trim().toUpperCase();
            mode = ModePresence.CODE;
            a = (horsLigne ? activiteRepository.findByDateLessThanEqual(jour)
                           : activiteRepository.findByDateLessThanEqualAndQrActifTrue(jour)).stream()
                    .filter(x -> (horsLigne ? enCoursLe(x, jour) : statut(x) == StatutActivite.EN_COURS)
                            && codeValide(x, code, moment))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Code invalide ou expiré : saisissez le code actuellement affiché."));
        } else {
            throw new IllegalArgumentException("Scannez le QR code ou saisissez le code de présence.");
        }
        if (a.getCohorteId() != null && !a.getCohorteId().equals(m.getCohorte().getId()))
            throw new ConflitException("Cette activité est réservée aux membres d'une autre cohorte.");
        if (horsLigne ? !enCoursLe(a, jour) : (!a.isQrActif() || statut(a) != StatutActivite.EN_COURS))
            throw new ConflitException(horsLigne
                    ? "L'activité n'avait pas lieu à la date du scan."
                    : "Les présences ne sont pas ouvertes pour cette activité.");
        Optional<Presence> existante = presenceRepository.findByActiviteIdAndMembreId(a.getId(), m.getId());
        LocalDateTime arrivee = LocalDateTime.ofInstant(moment, ZoneId.systemDefault());
        String motif = horsLigne ? "Hors connexion, synchronisé le "
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : null;
        Presence p = existante.orElseGet(() -> enregistrer(a, m, mode, arrivee, motif, null));
        if (existante.isEmpty()) {
            parcoursRepository.save(ParcoursSNJ.builder()
                    .membre(m).intitule("Participation — " + a.getTitre()).type("ACTIVITE")
                    .dateDebut(a.getDate()).dateFin(a.getDateFin()).lieu(a.getLieu()).build());
            historiqueService.enregistrer("PRESENCE", ENTITE, a.getId(), m.getIdentifiantSnj() + " · " + mode
                    + (horsLigne ? " · hors connexion" : ""));
        }
        return ConfirmationPresence.builder()
                .activiteId(a.getId()).activite(a.getTitre())
                .organisateur(a.getMobilisation() != null ? a.getMobilisation().getOrganisateur() : null)
                .lieu(a.getLieu()).arrivee(p.getDateHeure()).mode(p.getMode())
                .ajouteeAuParcours(true).dejaEnregistree(existante.isPresent())
                .build();
    }

    @Override @Transactional(readOnly = true)
    public List<PresenceDTO> mesPresences() {
        return presenceRepository.findByMembreIdOrderByDateHeureDesc(perimetre.membreIdCourant()).stream().map(this::toPresence).toList();
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private Presence enregistrer(Activite a, Membre m, ModePresence mode, LocalDateTime heure, String motif, String saisiePar) {
        return presenceRepository.save(Presence.builder().activite(a).membre(m).mode(mode).dateHeure(heure)
                .motif(motif).saisiePar(saisiePar).build());
    }

    /** Heure de scan déclarée : ni dans le futur (horloge du téléphone), ni plus vieille que 72 h. */
    private static Instant momentDuScan(Instant scanneLe) {
        Instant maintenant = Instant.now();
        if (scanneLe.isAfter(maintenant.plusSeconds(300)))
            throw new IllegalArgumentException("Heure du scan invalide : vérifiez l'heure de votre téléphone.");
        if (scanneLe.isBefore(maintenant.minus(Duration.ofHours(DELAI_HORS_LIGNE_HEURES))))
            throw new ConflitException("Présence scannée il y a plus de " + DELAI_HORS_LIGNE_HEURES
                    + " h : elle ne peut plus être envoyée. Contactez votre responsable.");
        return scanneLe;
    }

    /** L'activité se déroulait-elle ce jour-là (entre sa date de début et sa date de fin) ? */
    private static boolean enCoursLe(Activite a, LocalDate jour) {
        LocalDate fin = a.getDateFin() != null ? a.getDateFin() : a.getDate();
        return !jour.isBefore(a.getDate()) && !jour.isAfter(fin);
    }

    /** Le code affiché est valable pendant sa fenêtre de 30 s et la précédente (tolérance de lecture). */
    private boolean codeValide(Activite a, String code, Instant moment) {
        long fenetre = moment.getEpochSecond() / PERIODE_SECONDES;
        return code.equalsIgnoreCase(code(a, fenetre)) || code.equalsIgnoreCase(code(a, fenetre - 1));
    }

    private static String code(Activite a, long fenetre) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(a.getSecretQr().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(Long.toString(fenetre).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt((h[i] & 0xFF) % ALPHABET.length()));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de générer le code de présence", e);
        }
    }

    /** Statut effectif : clôture manuelle, sinon déduit des dates. */
    private static StatutActivite statut(Activite a) {
        if (a.getStatut() == StatutActivite.TERMINEE) return StatutActivite.TERMINEE;
        LocalDate aujourdhui = LocalDate.now();
        LocalDate fin = a.getDateFin() != null ? a.getDateFin() : a.getDate();
        if (aujourdhui.isBefore(a.getDate())) return StatutActivite.PLANIFIEE;
        if (aujourdhui.isAfter(fin)) return StatutActivite.TERMINEE;
        return StatutActivite.EN_COURS;
    }

    private void appliquer(Activite a, Edition dto) {
        if (dto.getDateFin() != null && dto.getDateFin().isBefore(dto.getDate()))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        if (dto.getHeureDebut() != null && dto.getHeureFin() != null && dto.getHeureFin().isBefore(dto.getHeureDebut()))
            throw new IllegalArgumentException("L'heure de fin doit être postérieure à l'heure de début.");
        User u = perimetre.utilisateurCourant();
        a.setTitre(dto.getTitre().trim());
        a.setDescription(dto.getDescription());
        a.setDate(dto.getDate());
        a.setDateFin(dto.getDateFin());
        a.setHeureDebut(dto.getHeureDebut());
        a.setHeureFin(dto.getHeureFin());
        a.setLieu(dto.getLieu());
        a.setRegion(u.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? u.getRegion() : dto.getRegion());
        // Activité créée par un responsable de cohorte : réservée à sa cohorte
        if (u.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE) a.setCohorteId(u.getCohorteId());
        a.setQrActif(dto.isQrActif());
        a.setMobilisation(dto.getMobilisationId() == null ? null : mobilisationRepository.findById(dto.getMobilisationId())
                .orElseThrow(() -> new EntityNotFoundException("Mobilisation non trouvée : " + dto.getMobilisationId())));
    }

    private Membre membreCourant() {
        return membreRepository.findById(perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Fiche membre introuvable."));
    }

    /**
     * Administrateurs : toutes les activités. Responsable régional : celles qu'il a créées ou de sa région.
     * Responsable de cohorte : celles qu'il a créées ou réservées à sa cohorte.
     */
    private boolean gerable(Activite a, User u) {
        return switch (u.getRole()) {
            case ROLE_RESPONSABLE_REGIONAL -> u.getUsername().equals(a.getCreePar())
                    || (a.getRegion() != null && a.getRegion().equalsIgnoreCase(u.getRegion()));
            case ROLE_RESPONSABLE_COHORTE -> u.getUsername().equals(a.getCreePar())
                    || (a.getCohorteId() != null && a.getCohorteId().equals(u.getCohorteId()));
            default -> true;
        };
    }

    private Activite trouverGerable(Long id) {
        Activite a = trouver(id);
        if (!gerable(a, perimetre.utilisateurCourant()))
            throw new AccessDeniedException("Cette activité ne fait pas partie de votre périmètre.");
        return a;
    }

    private Activite trouver(Long id) {
        return activiteRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Activité non trouvée : " + id));
    }

    private Resume toResume(Activite a) {
        long presents = presenceRepository.countByActiviteId(a.getId());
        Long inscrits = null;
        long excuses = 0;
        if (a.getMobilisation() != null) {
            inscrits = participationRepository.countByMobilisationIdAndReponse(a.getMobilisation().getId(), ReponseMobilisation.DISPONIBLE);
            excuses = participationRepository.countByMobilisationIdAndReponse(a.getMobilisation().getId(), ReponseMobilisation.INDISPONIBLE);
        }
        return Resume.builder()
                .id(a.getId()).titre(a.getTitre()).description(a.getDescription()).date(a.getDate()).dateFin(a.getDateFin())
                .heureDebut(a.getHeureDebut()).heureFin(a.getHeureFin()).lieu(a.getLieu()).region(a.getRegion())
                .statut(statut(a)).qrActif(a.isQrActif())
                .mobilisationId(a.getMobilisation() != null ? a.getMobilisation().getId() : null)
                .mobilisation(a.getMobilisation() != null ? a.getMobilisation().getTitre() : null)
                .compteurs(Compteurs.builder()
                        .inscrits(inscrits).presents(presents)
                        .tauxPresence(inscrits == null || inscrits == 0 ? 0 : (int) Math.round(presents * 100.0 / inscrits))
                        .arriveesDerniereHeure(presenceRepository.countByActiviteIdAndDateHeureAfter(a.getId(), LocalDateTime.now().minusHours(1)))
                        .absentsExcuses(excuses)
                        .saisiesManuelles(presenceRepository.countByActiviteIdAndMode(a.getId(), ModePresence.MANUEL))
                        .build())
                .build();
    }

    private PresenceDTO toPresence(Presence p) {
        return PresenceDTO.builder()
                .id(p.getId()).membreId(p.getMembre().getId()).identifiantSnj(p.getMembre().getIdentifiantSnj())
                .prenom(p.getMembre().getPrenom()).nom(p.getMembre().getNom())
                .dateHeure(p.getDateHeure()).mode(p.getMode()).motif(p.getMotif()).saisiePar(p.getSaisiePar())
                .build();
    }
}
