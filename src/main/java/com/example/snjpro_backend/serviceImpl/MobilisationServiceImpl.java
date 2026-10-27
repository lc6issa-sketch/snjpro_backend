package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.MobilisationDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.MobilisationServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MobilisationServiceImpl implements MobilisationServiceInterface {
    private static final String ENTITE = "MOBILISATION";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM");

    private final MobilisationRepository mobilisationRepository;
    private final ParticipationMobilisationRepository participationRepository;
    private final ParcoursSNJRepository parcoursRepository;
    private final SelectionMembres selection;
    private final NotificationServiceInterface notificationService;
    private final GenerateurFichiers generateur;
    private final EnvoiMessageService envoi;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;
    private final UserRepository userRepository;

    // =====================================================================
    // Gestion
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> lister(StatutMobilisation statut, String recherche) {
        User u = perimetre.utilisateurCourant();
        return mobilisationRepository.findAllByOrderByDateCreationDesc().stream()
                .filter(m -> statut == null || m.getStatut() == statut)
                .filter(m -> !StringUtils.hasText(recherche) || m.getTitre().toLowerCase().contains(recherche.trim().toLowerCase())
                        || (m.getLieu() != null && m.getLieu().toLowerCase().contains(recherche.trim().toLowerCase())))
                .filter(m -> visible(m, u))
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Statistiques statistiques() {
        LocalDateTime debutAnnee = LocalDate.now().withDayOfYear(1).atStartOfDay();
        User u = perimetre.utilisateurCourant();
        // Responsables : uniquement les mobilisations de leur périmètre
        List<Mobilisation> visibles = mobilisationRepository.findAllByOrderByDateCreationDesc().stream().filter(m -> visible(m, u)).toList();
        Set<Long> ids = visibles.stream().map(Mobilisation::getId).collect(Collectors.toSet());
        List<ParticipationMobilisation> annee = participationRepository.findByDateSollicitationAfter(debutAnnee).stream()
                .filter(p -> ids.contains(p.getMobilisation().getId())).toList();
        long repondus = annee.stream().filter(p -> p.getReponse() != ReponseMobilisation.EN_ATTENTE).count();
        long disponibles = annee.stream().filter(p -> p.getReponse() == ReponseMobilisation.DISPONIBLE).count();
        OptionalDouble delai = annee.stream().filter(p -> p.getDateReponse() != null)
                .mapToLong(p -> Duration.between(p.getDateSollicitation(), p.getDateReponse()).toMinutes()).average();
        return Statistiques.builder()
                .enCours(visibles.stream().filter(m -> m.getStatut() == StatutMobilisation.EN_COURS).count())
                .planifiees(visibles.stream().filter(m -> m.getStatut() == StatutMobilisation.PLANIFIEE).count())
                .cloturees(visibles.stream().filter(m -> m.getStatut() == StatutMobilisation.CLOTUREE).count())
                .membresSollicitesAnnee(annee.size())
                .tauxReponse(pourcentage(repondus, annee.size()))
                .delaiMoyenReponseHeures(delai.isPresent() ? Math.round(delai.getAsDouble() / 6.0) / 10.0 : null)
                .disponiblesConfirmes(disponibles)
                .pourcentageDisponibles(pourcentage(disponibles, annee.size()))
                .build();
    }

    @Override
    public Detail creer(Informations dto) {
        Mobilisation m = new Mobilisation();
        m.setStatut(StatutMobilisation.BROUILLON);
        m.setCreePar(perimetre.utilisateurCourant().getUsername());
        appliquer(m, dto);
        m = mobilisationRepository.save(m);
        historiqueService.enregistrer("CREATION", ENTITE, m.getId(), m.getTitre());
        return toDetail(m);
    }

    @Override
    public Detail modifierInformations(Long id, Informations dto) {
        Mobilisation m = trouverGerable(id);
        if (m.getStatut() == StatutMobilisation.CLOTUREE)
            throw new ConflitException("Une mobilisation clôturée ne peut plus être modifiée.");
        appliquer(m, dto);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, m.getTitre());
        return toDetail(mobilisationRepository.save(m));
    }

    @Override
    public Detail definirCriteres(Long id, Criteres c) {
        Mobilisation m = trouverGerable(id);
        exigerStatut(m, StatutMobilisation.BROUILLON, "Les critères ne peuvent plus être modifiés après l'envoi.");
        if (c.getAgeMin() != null && c.getAgeMax() != null && c.getAgeMin() > c.getAgeMax())
            throw new IllegalArgumentException("L'âge minimum doit être inférieur à l'âge maximum.");
        m.setCritereRegions(joindre(restreindreRegions(c.getRegions())));
        m.setCritereCompetences(joindre(c.getCompetences()));
        m.setCritereToutesCompetences(c.isToutesCompetences());
        m.setCritereCohortes(joindre(restreindreCohortes(c.getCohorteIds()).stream().map(String::valueOf).toList()));
        m.setCritereStatuts(joindre(c.getStatutsMembre().stream().map(Enum::name).toList()));
        m.setCritereSexe(c.getSexe());
        m.setCritereAgeMin(c.getAgeMin());
        m.setCritereAgeMax(c.getAgeMax());
        m.setCritereTelephoneVerifie(c.isTelephoneVerifieUniquement());
        m.setCritereExclureMobilises(c.isExclureDejaMobilises());
        return toDetail(mobilisationRepository.save(m));
    }

    @Override @Transactional(readOnly = true)
    public Apercu apercu(Long id, int limite) {
        Mobilisation m = trouverGerable(id);
        List<Membre> membres = selectionner(m);
        List<String> competences = separer(m.getCritereCompetences());
        long femmes = membres.stream().filter(x -> x.getSexe() == SexeEnum.FEMININ).count();
        long verifies = membres.stream().filter(x -> Boolean.TRUE.equals(x.getTelephoneVerifie())).count();
        Map<String, Long> parRegion = membres.stream().collect(Collectors.groupingBy(
                x -> x.getLocalisation() != null && x.getLocalisation().getRegion() != null ? x.getLocalisation().getRegion() : "Non renseignée",
                TreeMap::new, Collectors.counting()));
        return Apercu.builder()
                .total(membres.size())
                .parRegion(parRegion)
                .pourcentageFemmes(pourcentage(femmes, membres.size()))
                .pourcentageTelephoneVerifie(pourcentage(verifies, membres.size()))
                .estimationSms((int) membres.stream().filter(x -> StringUtils.hasText(x.getNumeroTelephone())).count())
                .membres(membres.stream()
                        .sorted(Comparator.comparingInt((Membre x) -> -SelectionMembres.competencesCorrespondantes(x, competences).size()))
                        .limit(Math.max(1, Math.min(limite, 500)))
                        .map(x -> MembreSelectionne.builder()
                                .membreId(x.getId()).identifiantSnj(x.getIdentifiantSnj())
                                .prenom(x.getPrenom()).nom(x.getNom()).region(region(x))
                                .cohorte(x.getCohorte().getNom())
                                .competencesCorrespondantes(SelectionMembres.competencesCorrespondantes(x, competences))
                                .telephoneVerifie(Boolean.TRUE.equals(x.getTelephoneVerifie()))
                                .build())
                        .toList())
                .build();
    }

    @Override
    public Detail envoyer(Long id, Envoi dto) {
        Mobilisation m = trouverGerable(id);
        exigerStatut(m, StatutMobilisation.BROUILLON, "Cette mobilisation a déjà été envoyée.");
        if (!dto.isCanalApplication() && !dto.isCanalSms() && !dto.isCanalEmail())
            throw new IllegalArgumentException("Choisissez au moins un canal d'envoi.");
        m.setMessage(StringUtils.hasText(dto.getMessage()) ? dto.getMessage().trim() : messageParDefaut(m));
        m.setCanalApplication(dto.isCanalApplication());
        m.setCanalSms(dto.isCanalSms());
        m.setCanalEmail(dto.isCanalEmail());
        Set<Long> exclus = new HashSet<>(dto.getMembresExclus());
        List<Membre> membres = selectionner(m).stream().filter(x -> !exclus.contains(x.getId())).toList();
        if (membres.isEmpty())
            throw new IllegalArgumentException("Aucun membre ne correspond aux critères : élargissez la sélection.");
        for (Membre x : membres) {
            participationRepository.save(ParticipationMobilisation.builder()
                    .mobilisation(m).membre(x).dateSollicitation(LocalDateTime.now()).build());
        }
        if (dto.getProgrammeLe() != null && dto.getProgrammeLe().isAfter(LocalDateTime.now())) {
            m.setEnvoiProgrammeLe(dto.getProgrammeLe());
            m.setStatut(StatutMobilisation.PLANIFIEE);
        } else {
            diffuser(m, membres);
        }
        historiqueService.enregistrer("ENVOI", ENTITE, id, m.getTitre() + " · " + membres.size() + " membre(s) sollicité(s)");
        return toDetail(mobilisationRepository.save(m));
    }

    @Override @Transactional(readOnly = true)
    public Detail obtenir(Long id) { return toDetail(trouverGerable(id)); }

    @Override @Transactional(readOnly = true)
    public Page<Participant> participants(Long id, ReponseMobilisation reponse, Pageable pageable) {
        Mobilisation m = trouverGerable(id);
        Page<ParticipationMobilisation> page = reponse == null
                ? participationRepository.findByMobilisationIdOrderByDateReponseDesc(id, pageable)
                : participationRepository.findByMobilisationIdAndReponseOrderByDateReponseDesc(id, reponse, pageable);
        List<String> competences = separer(m.getCritereCompetences());
        return page.map(p -> toParticipant(p, competences));
    }

    @Override @Transactional // écrit dans l'historique : pas de transaction en lecture seule
    public byte[] exporterParticipants(Long id, ReponseMobilisation reponse, FormatFichier format) {
        Mobilisation m = trouverGerable(id);
        List<String> competences = separer(m.getCritereCompetences());
        List<List<String>> lignes = participationRepository.findByMobilisationId(id).stream()
                .filter(p -> reponse == null || p.getReponse() == reponse)
                .map(p -> toParticipant(p, competences))
                .map(p -> List.of(p.getIdentifiantSnj(), p.getPrenom(), p.getNom(), nvl(p.getRegion()), nvl(p.getCompetence()),
                        p.getReponse().name(), nvl(p.getMotif()), p.getDateReponse() == null ? "" : p.getDateReponse().toString()))
                .toList();
        historiqueService.enregistrer("EXPORT", ENTITE, id, lignes.size() + " participant(s) · " + format);
        return generateur.tableau(new GenerateurFichiers.Tableau("Mobilisation - " + m.getTitre(),
                List.of("Identifiant", "Prénom", "Nom", "Région", "Compétence", "Réponse", "Motif", "Date de réponse"), lignes), format);
    }

    @Override
    public Detail relancer(Long id) {
        Mobilisation m = trouverGerable(id);
        exigerStatut(m, StatutMobilisation.EN_COURS, "Seule une mobilisation en cours peut être relancée.");
        relancerSansReponse(m);
        historiqueService.enregistrer("RELANCE", ENTITE, id, m.getTitre());
        return toDetail(mobilisationRepository.save(m));
    }

    @Override
    public Detail cloturer(Long id, Cloture dto) {
        Mobilisation m = trouverGerable(id);
        if (m.getStatut() == StatutMobilisation.CLOTUREE) throw new ConflitException("Cette mobilisation est déjà clôturée.");
        List<ParticipationMobilisation> disponibles = participationRepository.findByMobilisationIdAndReponse(id, ReponseMobilisation.DISPONIBLE);
        List<Membre> participants = disponibles.stream().map(ParticipationMobilisation::getMembre).toList();
        if (dto.isAjouterAuParcours()) {
            for (Membre x : participants) {
                parcoursRepository.save(ParcoursSNJ.builder()
                        .membre(x).intitule("Mobilisation — " + m.getTitre()).type("MOBILISATION")
                        .description(m.getDescription()).dateDebut(m.getDateDebut()).dateFin(m.getDateFin())
                        .lieu(m.getLieu()).build());
            }
        }
        if (dto.isRemercierParticipants()) {
            notificationService.notifierMembres(participants, TypeNotification.MOBILISATION, "Mobilisation clôturée",
                    "Merci pour votre participation : " + m.getTitre() + ".", false, "MOBILISATION", id);
        }
        m.setStatut(StatutMobilisation.CLOTUREE);
        m.setDateCloture(LocalDateTime.now());
        historiqueService.enregistrer("CLOTURE", ENTITE, id, m.getTitre() + " · " + participants.size() + " participant(s)");
        return toDetail(mobilisationRepository.save(m));
    }

    @Override
    public void supprimer(Long id) {
        Mobilisation m = trouverGerable(id);
        exigerStatut(m, StatutMobilisation.BROUILLON, "Seul un brouillon peut être supprimé ; clôturez la mobilisation.");
        mobilisationRepository.delete(m);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, m.getTitre());
    }

    /** Envoi des mobilisations programmées et relance automatique 2 jours avant la date limite. */
    @Scheduled(fixedDelay = 60_000)
    public void traitementsAutomatiques() {
        for (Mobilisation m : mobilisationRepository.findByStatutAndEnvoiProgrammeLeLessThanEqual(StatutMobilisation.PLANIFIEE, LocalDateTime.now())) {
            diffuser(m, participationRepository.findByMobilisationId(m.getId()).stream().map(ParticipationMobilisation::getMembre).toList());
            mobilisationRepository.save(m);
        }
        for (Mobilisation m : mobilisationRepository.findByStatut(StatutMobilisation.EN_COURS)) {
            if (m.getDateRelance() == null && m.getDateLimiteReponse() != null
                    && !LocalDate.now().isBefore(m.getDateLimiteReponse().minusDays(2))) {
                relancerSansReponse(m);
                mobilisationRepository.save(m);
            }
        }
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<MaMobilisation> mesMobilisations() {
        Long membreId = perimetre.membreIdCourant();
        return participationRepository.findByMembreIdOrderByDateSollicitationDesc(membreId).stream()
                .filter(p -> p.getMobilisation().getStatut() != StatutMobilisation.PLANIFIEE)
                .map(this::toMaMobilisation)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public MaMobilisation maMobilisation(Long id) {
        return toMaMobilisation(maParticipation(id));
    }

    @Override
    public MaMobilisation repondre(Long id, ReponseMembre dto) {
        ParticipationMobilisation p = maParticipation(id);
        Mobilisation m = p.getMobilisation();
        exigerStatut(m, StatutMobilisation.EN_COURS, "Cette mobilisation n'accepte plus de réponse.");
        if (m.getDateLimiteReponse() != null && LocalDate.now().isAfter(m.getDateLimiteReponse()))
            throw new ConflitException("La date limite de réponse est dépassée.");
        p.setReponse(dto.getDisponible() ? ReponseMobilisation.DISPONIBLE : ReponseMobilisation.INDISPONIBLE);
        p.setMotifIndisponibilite(dto.getDisponible() ? null : dto.getMotif());
        p.setDateReponse(LocalDateTime.now());
        participationRepository.save(p);
        historiqueService.enregistrer(dto.getDisponible() ? "DISPONIBILITE" : "INDISPONIBILITE", ENTITE, m.getId(),
                p.getMembre().getIdentifiantSnj());
        return toMaMobilisation(p);
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void diffuser(Mobilisation m, List<Membre> membres) {
        String titre = (m.isUrgent() ? "Mobilisation urgente — " : "Mobilisation — ") + m.getTitre();
        if (m.isCanalApplication())
            notificationService.notifierMembres(membres, TypeNotification.MOBILISATION, titre,
                    personnaliser(m.getMessage(), null), false, "MOBILISATION", m.getId());
        for (Membre x : membres) {
            if (m.isCanalSms()) envoi.sms(x.getNumeroTelephone(), personnaliser(m.getMessage(), x.getPrenom()));
            if (m.isCanalEmail()) envoi.email(x.getEmail(), titre, personnaliser(m.getMessage(), x.getPrenom()));
        }
        m.setStatut(StatutMobilisation.EN_COURS);
        m.setDateEnvoi(LocalDateTime.now());
    }

    private void relancerSansReponse(Mobilisation m) {
        List<ParticipationMobilisation> attente = participationRepository.findByMobilisationIdAndReponse(m.getId(), ReponseMobilisation.EN_ATTENTE);
        attente.forEach(p -> p.setRelances(p.getRelances() + 1));
        notificationService.notifierMembres(attente.stream().map(ParticipationMobilisation::getMembre).toList(),
                TypeNotification.MOBILISATION, "Rappel — " + m.getTitre(),
                "Votre réponse est attendue" + (m.getDateLimiteReponse() != null ? " avant le " + m.getDateLimiteReponse().format(JOUR) : "")
                        + ". Êtes-vous disponible ?", m.isCanalSms(), "MOBILISATION", m.getId());
        m.setDateRelance(LocalDateTime.now());
    }

    private List<Membre> selectionner(Mobilisation m) {
        Set<Long> exclure = m.isCritereExclureMobilises()
                ? participationRepository.membresMobilises(ReponseMobilisation.DISPONIBLE, List.of(StatutMobilisation.EN_COURS, StatutMobilisation.PLANIFIEE))
                : Set.of();
        List<StatutMembreEnum> statuts = separer(m.getCritereStatuts()).stream().map(StatutMembreEnum::valueOf).toList();
        return dansPerimetreDeLAuteur(m, selection.filtrer(SelectionMembres.Criteres.builder()
                .regions(separer(m.getCritereRegions()))
                .competences(separer(m.getCritereCompetences()))
                .toutesCompetences(m.isCritereToutesCompetences())
                .cohorteIds(separer(m.getCritereCohortes()).stream().map(Long::valueOf).toList())
                .statuts(statuts.isEmpty() ? List.of(StatutMembreEnum.ACTIF) : statuts)
                .sexe(m.getCritereSexe())
                .ageMin(m.getCritereAgeMin()).ageMax(m.getCritereAgeMax())
                .telephoneVerifieUniquement(m.isCritereTelephoneVerifie())
                .exclure(exclure)
                .build()));
    }

    private void appliquer(Mobilisation m, Informations dto) {
        if (dto.getDateFin() != null && dto.getDateFin().isBefore(dto.getDateDebut()))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        if (dto.getDateLimiteReponse() != null && dto.getDateLimiteReponse().isAfter(dto.getDateDebut()))
            throw new IllegalArgumentException("La date limite de réponse doit précéder le début de la mobilisation.");
        User u = perimetre.utilisateurCourant();
        m.setTitre(dto.getTitre().trim());
        m.setDescription(dto.getDescription());
        m.setTypeBesoin(dto.getTypeBesoin());
        m.setOrganisateur(dto.getOrganisateur());
        m.setDateDebut(dto.getDateDebut());
        m.setDateFin(dto.getDateFin());
        m.setHeureRendezVous(dto.getHeureRendezVous());
        m.setLieu(dto.getLieu());
        m.setRegion(u.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? u.getRegion() : dto.getRegion());
        m.setNombreVolontaires(dto.getNombreVolontaires());
        m.setDateLimiteReponse(dto.getDateLimiteReponse());
        m.setPriseEnCharge(joindre(dto.getPriseEnCharge()));
        m.setUrgent(dto.isUrgent());
    }

    private List<String> restreindreRegions(List<String> regions) {
        User u = perimetre.utilisateurCourant();
        return u.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? List.of(u.getRegion()) : regions;
    }

    /** Un responsable de cohorte ne mobilise que les membres de sa cohorte. */
    private List<Long> restreindreCohortes(List<Long> cohorteIds) {
        User u = perimetre.utilisateurCourant();
        return u.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE ? List.of(u.getCohorteId()) : cohorteIds;
    }

    /**
     * Membres sélectionnés limités au périmètre de l'auteur de la mobilisation (responsable régional ou de cohorte),
     * même si aucun critère n'a été enregistré.
     */
    private List<Membre> dansPerimetreDeLAuteur(Mobilisation m, List<Membre> membres) {
        User auteur = m.getCreePar() == null ? null : userRepository.findByUsername(m.getCreePar()).orElse(null);
        if (auteur == null || !perimetre.estResponsable(auteur)) return membres;
        return membres.stream().filter(x -> perimetre.couvre(auteur, x)).toList();
    }

    private boolean visible(Mobilisation m, User u) {
        return switch (u.getRole()) {
            case ROLE_RESPONSABLE_REGIONAL -> u.getUsername().equals(m.getCreePar())
                    || (m.getRegion() != null && m.getRegion().equalsIgnoreCase(u.getRegion()));
            case ROLE_RESPONSABLE_COHORTE -> u.getUsername().equals(m.getCreePar());
            default -> true;
        };
    }

    private Mobilisation trouverGerable(Long id) {
        Mobilisation m = mobilisationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Mobilisation non trouvée : " + id));
        if (!visible(m, perimetre.utilisateurCourant()))
            throw new AccessDeniedException("Cette mobilisation ne fait pas partie de votre périmètre.");
        return m;
    }

    private ParticipationMobilisation maParticipation(Long mobilisationId) {
        return participationRepository.findByMobilisationIdAndMembreId(mobilisationId, perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Vous n'êtes pas sollicité pour cette mobilisation."));
    }

    private static void exigerStatut(Mobilisation m, StatutMobilisation attendu, String message) {
        if (m.getStatut() != attendu) throw new ConflitException(message);
    }

    private Compteurs compteurs(Long id) {
        long total = participationRepository.countByMobilisationId(id);
        long dispo = participationRepository.countByMobilisationIdAndReponse(id, ReponseMobilisation.DISPONIBLE);
        long indispo = participationRepository.countByMobilisationIdAndReponse(id, ReponseMobilisation.INDISPONIBLE);
        return Compteurs.builder().sollicites(total).disponibles(dispo).indisponibles(indispo)
                .sansReponse(total - dispo - indispo).tauxReponse(pourcentage(dispo + indispo, total)).build();
    }

    private Resume toResume(Mobilisation m) {
        return Resume.builder()
                .id(m.getId()).titre(m.getTitre()).typeBesoin(m.getTypeBesoin()).lieu(m.getLieu()).region(m.getRegion())
                .dateDebut(m.getDateDebut()).dateFin(m.getDateFin()).statut(m.getStatut()).urgent(m.isUrgent())
                .envoiProgrammeLe(m.getEnvoiProgrammeLe())
                .nombreCibles((int) participationRepository.countByMobilisationId(m.getId()))
                .compteurs(compteurs(m.getId()))
                .build();
    }

    private Detail toDetail(Mobilisation m) {
        Informations info = new Informations();
        info.setTitre(m.getTitre());
        info.setDescription(m.getDescription());
        info.setTypeBesoin(m.getTypeBesoin());
        info.setOrganisateur(m.getOrganisateur());
        info.setDateDebut(m.getDateDebut());
        info.setDateFin(m.getDateFin());
        info.setHeureRendezVous(m.getHeureRendezVous());
        info.setLieu(m.getLieu());
        info.setRegion(m.getRegion());
        info.setNombreVolontaires(m.getNombreVolontaires());
        info.setDateLimiteReponse(m.getDateLimiteReponse());
        info.setPriseEnCharge(separer(m.getPriseEnCharge()));
        info.setUrgent(m.isUrgent());
        Criteres c = new Criteres();
        c.setRegions(separer(m.getCritereRegions()));
        c.setCompetences(separer(m.getCritereCompetences()));
        c.setToutesCompetences(m.isCritereToutesCompetences());
        c.setCohorteIds(separer(m.getCritereCohortes()).stream().map(Long::valueOf).toList());
        c.setStatutsMembre(separer(m.getCritereStatuts()).stream().map(StatutMembreEnum::valueOf).toList());
        c.setSexe(m.getCritereSexe());
        c.setAgeMin(m.getCritereAgeMin());
        c.setAgeMax(m.getCritereAgeMax());
        c.setTelephoneVerifieUniquement(m.isCritereTelephoneVerifie());
        c.setExclureDejaMobilises(m.isCritereExclureMobilises());
        Envoi e = new Envoi();
        e.setMessage(m.getMessage() != null ? m.getMessage() : messageParDefaut(m));
        e.setCanalApplication(m.isCanalApplication());
        e.setCanalSms(m.isCanalSms());
        e.setCanalEmail(m.isCanalEmail());
        e.setProgrammeLe(m.getEnvoiProgrammeLe());
        Compteurs cpt = m.getId() == null ? new Compteurs() : compteurs(m.getId());
        Map<LocalDate, Long> parJour = m.getId() == null ? Map.of() : participationRepository.findByMobilisationId(m.getId()).stream()
                .filter(p -> p.getDateReponse() != null)
                .collect(Collectors.groupingBy(p -> p.getDateReponse().toLocalDate(), TreeMap::new, Collectors.counting()));
        return Detail.builder()
                .id(m.getId()).informations(info).criteres(c).envoi(e).statut(m.getStatut()).compteurs(cpt)
                .objectif(m.getNombreVolontaires())
                .tauxObjectif(m.getNombreVolontaires() == null ? 0 : pourcentage(cpt.getDisponibles(), m.getNombreVolontaires()))
                .dateEnvoi(m.getDateEnvoi()).dateRelance(m.getDateRelance()).dateCloture(m.getDateCloture())
                .creePar(m.getCreePar()).reponsesParJour(parJour)
                .build();
    }

    private Participant toParticipant(ParticipationMobilisation p, List<String> competences) {
        Membre x = p.getMembre();
        List<String> communes = SelectionMembres.competencesCorrespondantes(x, competences);
        return Participant.builder()
                .participationId(p.getId()).membreId(x.getId()).identifiantSnj(x.getIdentifiantSnj())
                .prenom(x.getPrenom()).nom(x.getNom()).region(region(x))
                .competence(communes.isEmpty() ? null : String.join(", ", communes))
                .reponse(p.getReponse()).motif(p.getMotifIndisponibilite()).dateReponse(p.getDateReponse())
                .build();
    }

    private MaMobilisation toMaMobilisation(ParticipationMobilisation p) {
        Mobilisation m = p.getMobilisation();
        List<String> profil = separer(m.getCritereCompetences());
        return MaMobilisation.builder()
                .id(m.getId()).titre(m.getTitre()).description(m.getDescription()).organisateur(m.getOrganisateur())
                .lieu(m.getLieu()).dateDebut(m.getDateDebut()).dateFin(m.getDateFin())
                .heureRendezVous(m.getHeureRendezVous()).dateLimiteReponse(m.getDateLimiteReponse())
                .urgent(m.isUrgent()).statut(m.getStatut()).nombreVolontaires(m.getNombreVolontaires())
                .placesConfirmees(participationRepository.countByMobilisationIdAndReponse(m.getId(), ReponseMobilisation.DISPONIBLE))
                .profilRecherche(profil)
                .pourquoiVous(SelectionMembres.competencesCorrespondantes(p.getMembre(), profil))
                .priseEnCharge(separer(m.getPriseEnCharge()))
                .maReponse(p.getReponse()).dateReponse(p.getDateReponse())
                .build();
    }

    private static String messageParDefaut(Mobilisation m) {
        return "Bonjour {prenom}, le SNJ organise « " + m.getTitre() + " »"
                + (m.getLieu() != null ? " à " + m.getLieu() : "")
                + " à partir du " + m.getDateDebut().format(JOUR) + ". Votre profil correspond au besoin. "
                + "Confirmez votre disponibilité dans l'application SNJPro"
                + (m.getDateLimiteReponse() != null ? " avant le " + m.getDateLimiteReponse().format(JOUR) : "") + ".";
    }

    private static String region(Membre x) {
        return x.getLocalisation() != null ? x.getLocalisation().getRegion() : null;
    }

    private static int pourcentage(long partie, long total) {
        return total == 0 ? 0 : (int) Math.round(partie * 100.0 / total);
    }

    static List<String> separer(String valeur) {
        if (!StringUtils.hasText(valeur)) return new ArrayList<>();
        return Arrays.stream(valeur.split(",")).map(String::trim).filter(StringUtils::hasText).collect(Collectors.toCollection(ArrayList::new));
    }

    static String joindre(Collection<String> valeurs) {
        if (valeurs == null || valeurs.isEmpty()) return null;
        return valeurs.stream().filter(StringUtils::hasText).map(s -> s.trim().replace(",", " ")).distinct().collect(Collectors.joining(","));
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    private static String personnaliser(String message, String prenom) {
        String p = prenom == null ? "" : prenom;
        String r = message.replace("{prenom}", p).replace("{prénom}", p);
        return prenom == null ? r.replace("Bonjour ,", "Bonjour,") : r;
    }
}
