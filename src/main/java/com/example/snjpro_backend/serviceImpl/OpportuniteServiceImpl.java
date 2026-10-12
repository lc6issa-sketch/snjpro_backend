package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.OpportuniteDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.*;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import com.example.snjpro_backend.serviceInterface.OpportuniteServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OpportuniteServiceImpl implements OpportuniteServiceInterface {
    private static final String ENTITE = "OPPORTUNITE";

    private final OpportuniteRepository opportuniteRepository;
    private final CandidatureRepository candidatureRepository;
    private final FavoriOpportuniteRepository favoriRepository;
    private final MembreRepository membreRepository;
    private final SelectionMembres selection;
    private final NotificationServiceInterface notificationService;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Gestion
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> lister(TypeOpportunite type, StatutOpportunite statut, String recherche) {
        return opportuniteRepository.findAllByOrderByDateCreationDesc().stream()
                .filter(o -> type == null || o.getType() == type)
                .filter(o -> statut == null || o.getStatut() == statut)
                .filter(o -> !StringUtils.hasText(recherche) || (o.getIntitule() + " " + o.getOrganisation()).toLowerCase()
                        .contains(recherche.trim().toLowerCase()))
                .map(o -> toResume(o, null))
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Compteurs compteurs() {
        List<Opportunite> toutes = opportuniteRepository.findAll();
        return Compteurs.builder()
                .toutes(toutes.size())
                .emplois(toutes.stream().filter(o -> o.getType() == TypeOpportunite.EMPLOI).count())
                .stages(toutes.stream().filter(o -> o.getType() == TypeOpportunite.STAGE).count())
                .formations(toutes.stream().filter(o -> o.getType() == TypeOpportunite.FORMATION).count())
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Detail obtenir(Long id) { return toDetail(trouver(id), null); }

    @Override
    public Detail creer(Edition dto) {
        Opportunite o = new Opportunite();
        appliquer(o, dto);
        o.setPubliePar(perimetre.utilisateurCourant().getUsername());
        o = opportuniteRepository.save(o);
        historiqueService.enregistrer("CREATION", ENTITE, o.getId(), o.getIntitule());
        return dto.isPublier() ? publier(o.getId()) : toDetail(o, null);
    }

    @Override
    public Detail modifier(Long id, Edition dto) {
        Opportunite o = trouver(id);
        if (o.getStatut() == StatutOpportunite.CLOTUREE) throw new ConflitException("Une opportunité clôturée ne peut plus être modifiée.");
        appliquer(o, dto);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, o.getIntitule());
        o = opportuniteRepository.save(o);
        return dto.isPublier() && o.getStatut() == StatutOpportunite.BROUILLON ? publier(id) : toDetail(o, null);
    }

    @Override
    public Detail publier(Long id) {
        Opportunite o = trouver(id);
        if (o.getStatut() != StatutOpportunite.BROUILLON) throw new ConflitException("Cette opportunité est déjà publiée.");
        o.setStatut(StatutOpportunite.OUVERTE);
        o.setDatePublication(LocalDateTime.now());
        List<Membre> cibles = cibles(o);
        o.setMembresNotifies(o.isNotifierMembres() ? cibles.size() : 0);
        if (o.isNotifierMembres()) {
            notificationService.notifierMembres(cibles, TypeNotification.INFORMATION,
                    "Nouvelle opportunité : " + o.getIntitule(), o.getOrganisation() + (o.getLieu() != null ? " · " + o.getLieu() : ""),
                    false, "OPPORTUNITE", id);
        }
        historiqueService.enregistrer("PUBLICATION", ENTITE, id, o.getIntitule() + " · " + o.getMembresNotifies() + " membre(s) notifié(s)");
        return toDetail(opportuniteRepository.save(o), null);
    }

    @Override
    public Detail cloturer(Long id) {
        Opportunite o = trouver(id);
        o.setStatut(StatutOpportunite.CLOTUREE);
        historiqueService.enregistrer("CLOTURE", ENTITE, id, o.getIntitule());
        return toDetail(opportuniteRepository.save(o), null);
    }

    @Override
    public void supprimer(Long id) {
        Opportunite o = trouver(id);
        if (candidatureRepository.countByOpportuniteId(id) > 0)
            throw new ConflitException("Des membres ont déjà postulé : clôturez l'opportunité plutôt que de la supprimer.");
        favoriRepository.findAll().stream().filter(f -> f.getOpportunite().getId().equals(id)).forEach(favoriRepository::delete);
        opportuniteRepository.delete(o);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, o.getIntitule());
    }

    @Override @Transactional(readOnly = true)
    public List<CandidatureDTO> candidatures(Long id) {
        trouver(id);
        return candidatureRepository.findByOpportuniteIdOrderByScoreDesc(id).stream().map(this::toCandidature).toList();
    }

    @Override
    public CandidatureDTO changerStatutCandidature(Long candidatureId, StatutCandidature statut) {
        Candidature c = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new EntityNotFoundException("Candidature non trouvée : " + candidatureId));
        c.setStatut(statut);
        String message = switch (statut) {
            case PRESELECTIONNEE -> "Votre candidature a été présélectionnée.";
            case ENTRETIEN -> "Vous êtes convoqué(e) à un entretien. Vous serez contacté(e) prochainement.";
            case RETENUE -> "Félicitations, votre candidature a été retenue !";
            case NON_RETENUE -> "Votre candidature n'a pas été retenue cette fois-ci.";
            default -> null;
        };
        if (message != null)
            notificationService.notifierMembres(List.of(c.getMembre()), TypeNotification.INFORMATION,
                    c.getOpportunite().getIntitule(), message, false, "OPPORTUNITE", c.getOpportunite().getId());
        historiqueService.enregistrer("CANDIDATURE_" + statut, ENTITE, c.getOpportunite().getId(), c.getMembre().getIdentifiantSnj());
        return toCandidature(candidatureRepository.save(c));
    }

    @Override @Transactional(readOnly = true)
    public Map<String, Object> estimerCorrespondances(List<String> competences, String region) {
        List<Membre> membres = selection.filtrer(SelectionMembres.Criteres.builder()
                .competences(competences).regions(StringUtils.hasText(region) ? List.of(region) : null).build());
        return Map.of("nombreMembres", membres.size(), "competences", competences == null ? List.of() : competences,
                "region", region == null ? "" : region);
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> pourMoi(TypeOpportunite type, boolean recommandeesUniquement) {
        Membre m = membreCourant();
        return opportuniteRepository.findByStatutOrderByDatePublicationDesc(StatutOpportunite.OUVERTE).stream()
                .filter(o -> o.getDateLimite() == null || !o.getDateLimite().isBefore(LocalDate.now()))
                .filter(o -> type == null || o.getType() == type)
                .filter(o -> !"REGION".equals(o.getPublicCible()) || memeRegion(m, o.getRegion()))
                .map(o -> toResume(o, m))
                .filter(r -> !recommandeesUniquement || r.getScore() >= 50)
                .sorted(Comparator.comparing(Resume::getScore).reversed())
                .toList();
    }

    @Override
    public Detail consulter(Long id) {
        Membre m = membreCourant();
        Opportunite o = trouver(id);
        if (o.getStatut() == StatutOpportunite.BROUILLON) throw new EntityNotFoundException("Opportunité non trouvée : " + id);
        o.setVues(o.getVues() + 1);
        return toDetail(opportuniteRepository.save(o), m);
    }

    @Override
    public CandidatureDTO postuler(Long id, Postuler dto) {
        Membre m = membreCourant();
        Opportunite o = trouver(id);
        if (o.getStatut() != StatutOpportunite.OUVERTE) throw new ConflitException("Cette opportunité n'accepte plus de candidature.");
        if (o.getDateLimite() != null && o.getDateLimite().isBefore(LocalDate.now()))
            throw new ConflitException("La date limite de candidature est dépassée.");
        if (candidatureRepository.findByOpportuniteIdAndMembreId(id, m.getId()).isPresent())
            throw new ConflitException("Vous avez déjà postulé à cette opportunité.");
        Candidature c = candidatureRepository.save(Candidature.builder()
                .opportunite(o).membre(m).score(score(o, m)).message(dto == null ? null : dto.getMessage())
                .dateCandidature(LocalDateTime.now()).build());
        historiqueService.enregistrer("CANDIDATURE", ENTITE, id, m.getIdentifiantSnj());
        return toCandidature(c);
    }

    @Override
    public Resume basculerFavori(Long id) {
        Membre m = membreCourant();
        Opportunite o = trouver(id);
        Optional<FavoriOpportunite> f = favoriRepository.findByOpportuniteIdAndMembreId(id, m.getId());
        if (f.isPresent()) favoriRepository.delete(f.get());
        else favoriRepository.save(FavoriOpportunite.builder().opportunite(o).membreId(m.getId()).dateAjout(LocalDateTime.now()).build());
        return toResume(o, m);
    }

    @Override @Transactional(readOnly = true)
    public List<CandidatureDTO> mesCandidatures() {
        return candidatureRepository.findByMembreIdOrderByDateCandidatureDesc(perimetre.membreIdCourant()).stream()
                .map(this::toCandidature).toList();
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void appliquer(Opportunite o, Edition dto) {
        if (dto.getDateLimite() != null && dto.getDateLimite().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("La date limite de candidature doit être future.");
        if ("REGION".equals(dto.getPublicCible()) && !StringUtils.hasText(dto.getRegion()))
            throw new IllegalArgumentException("Précisez la région visée.");
        o.setType(dto.getType());
        o.setIntitule(dto.getIntitule().trim());
        o.setOrganisation(dto.getOrganisation().trim());
        o.setLieu(dto.getLieu());
        o.setTypeContrat(dto.getTypeContrat());
        o.setDateLimite(dto.getDateLimite());
        o.setDescription(dto.getDescription());
        o.setCompetences(MobilisationServiceImpl.joindre(dto.getCompetences()));
        o.setPublicCible(dto.getPublicCible() == null ? "COMPETENCES" : dto.getPublicCible());
        o.setRegion(dto.getRegion());
        o.setNotifierMembres(dto.isNotifierMembres());
        o.setPortailPublic(dto.isPortailPublic());
    }

    private List<Membre> cibles(Opportunite o) {
        List<String> competences = MobilisationServiceImpl.separer(o.getCompetences());
        return switch (o.getPublicCible() == null ? "COMPETENCES" : o.getPublicCible()) {
            case "TOUS" -> membreRepository.findAllNonArchives();
            case "REGION" -> selection.filtrer(SelectionMembres.Criteres.builder().regions(List.of(o.getRegion())).build());
            default -> competences.isEmpty() ? membreRepository.findAllNonArchives()
                    : selection.filtrer(SelectionMembres.Criteres.builder().competences(competences)
                    .regions(o.getRegion() != null ? List.of(o.getRegion()) : null).build());
        };
    }

    /** Part des compétences recherchées que possède le membre (50 si l'offre n'en précise aucune). */
    private static int score(Opportunite o, Membre m) {
        List<String> demandees = MobilisationServiceImpl.separer(o.getCompetences());
        if (demandees.isEmpty()) return 50;
        return (int) Math.round(SelectionMembres.competencesCorrespondantes(m, demandees).size() * 100.0 / demandees.size());
    }

    private static boolean memeRegion(Membre m, String region) {
        return region != null && m.getLocalisation() != null && region.equalsIgnoreCase(m.getLocalisation().getRegion());
    }

    private Membre membreCourant() {
        return membreRepository.findById(perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Fiche membre introuvable."));
    }

    private Opportunite trouver(Long id) {
        return opportuniteRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Opportunité non trouvée : " + id));
    }

    private Resume toResume(Opportunite o, Membre m) {
        return Resume.builder()
                .id(o.getId()).type(o.getType()).intitule(o.getIntitule()).organisation(o.getOrganisation())
                .lieu(o.getLieu()).typeContrat(o.getTypeContrat()).dateLimite(o.getDateLimite())
                .competences(MobilisationServiceImpl.separer(o.getCompetences())).statut(o.getStatut())
                .nombreCandidatures(o.getId() == null ? 0 : candidatureRepository.countByOpportuniteId(o.getId()))
                .vues(o.getVues()).datePublication(o.getDatePublication())
                .score(m == null ? null : score(o, m))
                .favori(m == null ? null : favoriRepository.findByOpportuniteIdAndMembreId(o.getId(), m.getId()).isPresent())
                .candidatureEnvoyee(m == null ? null : candidatureRepository.findByOpportuniteIdAndMembreId(o.getId(), m.getId()).isPresent())
                .build();
    }

    private Detail toDetail(Opportunite o, Membre m) {
        return Detail.builder()
                .resume(toResume(o, m)).description(o.getDescription()).publicCible(o.getPublicCible()).region(o.getRegion())
                .notifierMembres(o.isNotifierMembres()).portailPublic(o.isPortailPublic())
                .membresNotifies(o.getMembresNotifies()).publiePar(o.getPubliePar())
                .competencesCorrespondantes(m == null ? null
                        : SelectionMembres.competencesCorrespondantes(m, MobilisationServiceImpl.separer(o.getCompetences())))
                .build();
    }

    private CandidatureDTO toCandidature(Candidature c) {
        return CandidatureDTO.builder()
                .id(c.getId()).opportuniteId(c.getOpportunite().getId()).opportunite(c.getOpportunite().getIntitule())
                .organisation(c.getOpportunite().getOrganisation())
                .membreId(c.getMembre().getId()).identifiantSnj(c.getMembre().getIdentifiantSnj())
                .prenom(c.getMembre().getPrenom()).nom(c.getMembre().getNom())
                .score(c.getScore()).statut(c.getStatut()).message(c.getMessage()).dateCandidature(c.getDateCandidature())
                .build();
    }
}
