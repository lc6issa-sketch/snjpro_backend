package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.CohorteDTO;
import com.example.snjpro_backend.entity.Cohorte;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.serviceInterface.CohorteServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CohorteServiceImpl implements CohorteServiceInterface {
    private static final String ENTITE = "COHORTE";

    private final CohorteRepository cohorteRepository;
    private final HistoriqueServiceInterface historiqueService;
    private final com.example.snjpro_backend.repository.MembreRepository membreRepository;
    private final com.example.snjpro_backend.repository.UserRepository userRepository;
    private final com.example.snjpro_backend.repository.ParticipationMobilisationRepository participationRepository;
    private final com.example.snjpro_backend.serviceInterface.NotificationServiceInterface notificationService;
    private final com.example.snjpro_backend.security.PerimetreService perimetre;

    @Override
    public CohorteDTO creerCohorte(CohorteDTO dto) {
        String code = normaliserCode(dto.getCode());
        if (code != null && cohorteRepository.existsByCode(code))
            throw new ConflitException("Une cohorte avec le code '" + code + "' existe déjà.");
        verifierDates(dto);
        Cohorte cohorte = Cohorte.builder()
                .nom(dto.getNom().trim())
                .code(code)
                .description(dto.getDescription())
                .anneeDebut(dto.getAnneeDebut())
                .anneeFin(dto.getAnneeFin())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .statut(dto.getStatut() != null ? dto.getStatut() : StatutCohorteEnum.EN_FORMATION)
                .region(dto.getRegion())
                .responsable(dto.getResponsable())
                .build();
        appliquerComplements(cohorte, dto);
        cohorte = cohorteRepository.save(cohorte);
        historiqueService.enregistrer("CREATION", ENTITE, cohorte.getId(), cohorte.getNom());
        return toDTO(cohorte);
    }

    @Override
    public CohorteDTO modifierCohorte(Long id, CohorteDTO dto) {
        Cohorte cohorte = trouver(id);
        String code = normaliserCode(dto.getCode());
        if (code != null && !code.equals(cohorte.getCode()) && cohorteRepository.existsByCode(code))
            throw new ConflitException("Une cohorte avec le code '" + code + "' existe déjà.");
        verifierDates(dto);
        cohorte.setNom(dto.getNom().trim());
        cohorte.setCode(code);
        cohorte.setDescription(dto.getDescription());
        cohorte.setAnneeDebut(dto.getAnneeDebut());
        cohorte.setAnneeFin(dto.getAnneeFin());
        cohorte.setDateDebut(dto.getDateDebut());
        cohorte.setDateFin(dto.getDateFin());
        if (dto.getStatut() != null) cohorte.setStatut(dto.getStatut());
        cohorte.setRegion(dto.getRegion());
        cohorte.setResponsable(dto.getResponsable());
        appliquerComplements(cohorte, dto);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, cohorte.getNom());
        return toDTO(cohorteRepository.save(cohorte));
    }

    @Override @Transactional(readOnly = true)
    public CohorteDTO obtenirCohorte(Long id) {
        perimetre.exigerCohorte(id);
        return toDTO(trouver(id));
    }

    @Override @Transactional(readOnly = true)
    public List<CohorteDTO> obtenirToutesLesCohortes(StatutCohorteEnum statut, String nom) {
        com.example.snjpro_backend.entity.User u = perimetre.utilisateurCourant();
        return cohorteRepository.findAll(Sort.by(Sort.Direction.DESC, "anneeDebut").and(Sort.by("nom"))).stream()
                .filter(c -> perimetre.voitCohorte(u, c.getId()))
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> !StringUtils.hasText(nom) || c.getNom().toLowerCase().contains(nom.trim().toLowerCase()))
                .map(this::toDTO)
                .toList();
    }

    @Override
    public CohorteDTO archiverCohorte(Long id) {
        Cohorte cohorte = trouver(id);
        cohorte.setStatut(StatutCohorteEnum.ARCHIVEE);
        historiqueService.enregistrer("ARCHIVAGE", ENTITE, id, cohorte.getNom());
        return toDTO(cohorteRepository.save(cohorte));
    }

    @Override
    public CohorteDTO reactiverCohorte(Long id) {
        Cohorte cohorte = trouver(id);
        cohorte.setStatut(StatutCohorteEnum.LIBEREE);
        historiqueService.enregistrer("REACTIVATION", ENTITE, id, cohorte.getNom());
        return toDTO(cohorteRepository.save(cohorte));
    }

    @Override
    public void supprimerCohorte(Long id) {
        Cohorte cohorte = trouver(id);
        long nombreMembres = cohorteRepository.countMembresInCohorte(id);
        if (nombreMembres > 0)
            throw new ConflitException("Impossible de supprimer la cohorte : elle contient " + nombreMembres
                    + " membre(s). Archivez-la plutôt.");
        cohorteRepository.delete(cohorte);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, cohorte.getNom());
    }

    @Override @Transactional(readOnly = true)
    public boolean existeParCode(String code) { return cohorteRepository.existsByCode(code); }

    @Override @Transactional(readOnly = true)
    public long compterMembresCohorte(Long id) {
        perimetre.exigerCohorte(id);
        trouver(id);
        return cohorteRepository.countMembresInCohorte(id);
    }

    @Override
    public CohorteDTO changerStatut(Long id, StatutCohorteEnum statut, java.time.LocalDate dateEffet, String motif, boolean notifierMembres) {
        Cohorte cohorte = trouver(id);
        StatutCohorteEnum ancien = cohorte.getStatut();
        if (ancien == statut) throw new ConflitException("La cohorte est déjà au statut " + statut + ".");
        cohorte.setStatut(statut);
        cohorte = cohorteRepository.save(cohorte);
        historiqueService.enregistrer("CHANGEMENT_STATUT", ENTITE, id, cohorte.getNom() + " : " + ancien + " → " + statut
                + (dateEffet != null ? " au " + dateEffet : "") + (motif != null ? " · " + motif : ""));
        if (notifierMembres) {
            java.util.List<com.example.snjpro_backend.entity.Membre> membres = membreRepository.findAllNonArchives().stream()
                    .filter(m -> m.getCohorte().getId().equals(id)).toList();
            notificationService.notifierMembres(membres, com.example.snjpro_backend.enumeration.Enumerations.TypeNotification.INFORMATION,
                    cohorte.getNom(), "Le statut de votre cohorte passe à « " + libelle(statut) + " »"
                            + (dateEffet != null ? " à compter du " + dateEffet.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "")
                            + (motif != null ? " (" + motif + ")" : "") + ".", false, "COHORTE", id);
        }
        return toDTO(cohorte);
    }

    @Override @Transactional(readOnly = true)
    public java.util.Map<String, Object> tableauDeBord(Long id) {
        perimetre.exigerCohorte(id);
        Cohorte c = trouver(id);
        // Responsable régional : uniquement les membres de sa région
        java.util.List<com.example.snjpro_backend.entity.Membre> membres = membreRepository.findAll().stream()
                .filter(m -> m.getCohorte().getId().equals(id)).filter(perimetre::couvre).toList();
        long actifs = membres.stream().filter(m -> m.getStatut() == com.example.snjpro_backend.enumeration.StatutMembreEnum.ACTIF).count();
        long complets = membres.stream().filter(CompletudeProfil::complet).count();
        java.time.LocalDateTime debutAnnee = java.time.LocalDate.now().withDayOfYear(1).atStartOfDay();
        long mobilises = participationRepository.findByDateSollicitationAfter(debutAnnee).stream()
                .filter(p -> p.getMembre().getCohorte().getId().equals(id)
                        && p.getReponse() == com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation.DISPONIBLE)
                .map(p -> p.getMembre().getId()).distinct().count();
        long nbMobilisations = participationRepository.findByDateSollicitationAfter(debutAnnee).stream()
                .filter(p -> p.getMembre().getCohorte().getId().equals(id)).map(p -> p.getMobilisation().getId()).distinct().count();
        java.util.Map<String, Long> situation = new java.util.TreeMap<>();
        membres.stream().filter(m -> m.getStatut() == com.example.snjpro_backend.enumeration.StatutMembreEnum.ACTIF)
                .forEach(m -> situation.merge(m.getSituationProfessionnelle() == null ? "Non renseignée"
                        : StatistiqueServiceImpl.libelleStatut(m.getSituationProfessionnelle().getStatut()), 1L, Long::sum));
        java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("cohorte", toDTO(c));
        r.put("recruesInitiales", c.getRecruesInitiales());
        r.put("membres", membres.size());
        r.put("membresActifs", actifs);
        r.put("membresInactifs", membres.size() - actifs);
        r.put("pourcentageActifs", c.getRecruesInitiales() == null || c.getRecruesInitiales() == 0 ? null : Math.round(actifs * 100.0 / c.getRecruesInitiales()));
        r.put("pourcentageProfilsAJour", membres.isEmpty() ? 0 : Math.round(complets * 100.0 / membres.size()));
        r.put("mobilisesAnnee", mobilises);
        r.put("mobilisationsAnnee", nbMobilisations);
        r.put("dureeMois", c.getDateDebut() != null && c.getDateFin() != null
                ? java.time.temporal.ChronoUnit.MONTHS.between(c.getDateDebut(), c.getDateFin().plusDays(1)) : null);
        r.put("situationActuelle", situation);
        r.put("compagnies", membres.stream().map(com.example.snjpro_backend.entity.Membre::getCompagnie).filter(StringUtils::hasText).distinct().sorted().toList());
        return r;
    }

    @Override @Transactional(readOnly = true)
    public java.util.Map<String, Long> compteurs() {
        com.example.snjpro_backend.entity.User u = perimetre.utilisateurCourant();
        List<Cohorte> visibles = cohorteRepository.findAll().stream().filter(c -> perimetre.voitCohorte(u, c.getId())).toList();
        java.util.Map<String, Long> r = new java.util.LinkedHashMap<>();
        r.put("TOUTES", (long) visibles.size());
        for (StatutCohorteEnum s : StatutCohorteEnum.values()) r.put(s.name(), visibles.stream().filter(c -> c.getStatut() == s).count());
        return r;
    }

    private void appliquerComplements(Cohorte c, CohorteDTO dto) {
        c.setNumero(dto.getNumero());
        c.setCentre(dto.getCentre());
        c.setRecruesInitiales(dto.getRecruesInitiales());
        c.setNombreCompagnies(dto.getNombreCompagnies());
        c.setNombreSections(dto.getNombreSections());
        if (dto.getResponsableId() != null) {
            com.example.snjpro_backend.entity.User u = userRepository.findById(dto.getResponsableId())
                    .orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + dto.getResponsableId()));
            c.setResponsableId(u.getId());
            c.setResponsable(((u.getPrenom() == null ? "" : u.getPrenom()) + " " + (u.getNom() == null ? "" : u.getNom())).trim());
        } else {
            c.setResponsableId(null);
        }
    }

    private static String libelle(StatutCohorteEnum s) {
        return switch (s) {
            case EN_FORMATION -> "En formation";
            case EN_RESERVE -> "En réserve";
            case LIBEREE -> "Libérée";
            case ACTIVE -> "Active";
            case TERMINEE -> "Terminée";
            case ARCHIVEE -> "Archivée";
        };
    }

    private Cohorte trouver(Long id) {
        return cohorteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + id));
    }

    private String normaliserCode(String code) {
        return StringUtils.hasText(code) ? code.trim().toUpperCase() : null;
    }

    private void verifierDates(CohorteDTO dto) {
        if (dto.getAnneeDebut() != null && dto.getAnneeFin() != null && dto.getAnneeFin() < dto.getAnneeDebut())
            throw new IllegalArgumentException("L'année de fin doit être postérieure ou égale à l'année de début.");
        if (dto.getDateDebut() != null && dto.getDateFin() != null && dto.getDateFin().isBefore(dto.getDateDebut()))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
    }

    private CohorteDTO toDTO(Cohorte cohorte) {
        CohorteDTO dto = new CohorteDTO();
        dto.setId(cohorte.getId());
        dto.setNom(cohorte.getNom());
        dto.setCode(cohorte.getCode());
        dto.setDescription(cohorte.getDescription());
        dto.setAnneeDebut(cohorte.getAnneeDebut());
        dto.setAnneeFin(cohorte.getAnneeFin());
        dto.setDateDebut(cohorte.getDateDebut());
        dto.setDateFin(cohorte.getDateFin());
        dto.setStatut(cohorte.getStatut());
        dto.setRegion(cohorte.getRegion());
        dto.setResponsable(cohorte.getResponsable());
        dto.setNombreMembres(cohorteRepository.countMembresInCohorte(cohorte.getId()));
        dto.setNumero(cohorte.getNumero());
        dto.setCentre(cohorte.getCentre());
        dto.setRecruesInitiales(cohorte.getRecruesInitiales());
        dto.setNombreCompagnies(cohorte.getNombreCompagnies());
        dto.setNombreSections(cohorte.getNombreSections());
        dto.setResponsableId(cohorte.getResponsableId());
        dto.setResponsableNom(cohorte.getResponsable());
        java.util.List<com.example.snjpro_backend.entity.Membre> membres = cohorte.getMembres();
        dto.setMembresActifs(membres.stream().filter(m -> m.getStatut() == com.example.snjpro_backend.enumeration.StatutMembreEnum.ACTIF).count());
        dto.setPourcentageProfilsAJour(membres.isEmpty() ? 0
                : (int) Math.round(membres.stream().filter(CompletudeProfil::complet).count() * 100.0 / membres.size()));
        return dto;
    }
}
