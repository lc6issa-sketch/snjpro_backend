package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.CompetenceReferentielDTOs.*;
import com.example.snjpro_backend.entity.CompetenceReferentiel;
import com.example.snjpro_backend.entity.MembreCompetence;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.NiveauCompetenceEnum;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.CompetenceReferentielRepository;
import com.example.snjpro_backend.repository.MembreCompetenceRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.CompetenceReferentielServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CompetenceReferentielServiceImpl implements CompetenceReferentielServiceInterface {
    private static final String ENTITE = "COMPETENCE";
    private static final String SANS_CATEGORIE = "Autres";

    private final CompetenceReferentielRepository referentielRepository;
    private final MembreCompetenceRepository membreCompetenceRepository;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    @Override
    public List<Competence> lister(String categorie, String recherche, String tri, boolean toutes) {
        synchroniser();
        Long cohorteId = cohorteRestreinte(toutes);
        Map<String, List<MembreCompetence>> parNom = indexMembres(cohorteId);
        Comparator<Competence> ordre = switch (tri == null ? "MEMBRES" : tri.toUpperCase()) {
            case "NOM" -> Comparator.comparing(Competence::getNom, String.CASE_INSENSITIVE_ORDER);
            case "RECENTES" -> Comparator.comparing((Competence c) -> c.getAnneeAjout() == null ? 0 : c.getAnneeAjout()).reversed();
            default -> Comparator.comparingLong(Competence::getNombreMembres).reversed();
        };
        return referentielRepository.findAll().stream()
                .filter(c -> cohorteId == null || parNom.containsKey(cle(c.getNom())))
                .filter(c -> !StringUtils.hasText(categorie) || categorie.equalsIgnoreCase(categorieDe(c)))
                .filter(c -> !StringUtils.hasText(recherche) || c.getNom().toLowerCase().contains(recherche.trim().toLowerCase()))
                .map(c -> toDTO(c, parNom))
                .sorted(ordre)
                .toList();
    }

    @Override
    public List<Categorie> categories(boolean toutes) {
        synchroniser();
        Long cohorteId = cohorteRestreinte(toutes);
        Map<String, List<MembreCompetence>> parNom = indexMembres(cohorteId);
        Map<String, List<CompetenceReferentiel>> groupes = referentielRepository.findAll().stream()
                .filter(c -> cohorteId == null || parNom.containsKey(cle(c.getNom())))
                .collect(Collectors.groupingBy(this::categorieDe, TreeMap::new, Collectors.toList()));
        return groupes.entrySet().stream()
                .map(e -> Categorie.builder().nom(e.getKey()).nombreCompetences(e.getValue().size())
                        .nombreMembres(e.getValue().stream().flatMap(c -> parNom.getOrDefault(cle(c.getNom()), List.of()).stream())
                                .map(mc -> mc.getMembre().getId()).distinct().count())
                        .build())
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Competence obtenir(Long id) { return toDTO(trouver(id), indexMembres()); }

    @Override
    public Competence creer(Edition dto) {
        if (referentielRepository.existsByNomIgnoreCase(dto.getNom().trim()))
            throw new ConflitException("La compétence « " + dto.getNom().trim() + " » existe déjà.");
        CompetenceReferentiel c = referentielRepository.save(CompetenceReferentiel.builder()
                .nom(dto.getNom().trim()).categorie(vide(dto.getCategorie())).description(dto.getDescription()).build());
        historiqueService.enregistrer("CREATION", ENTITE, c.getId(), c.getNom());
        return toDTO(c, indexMembres());
    }

    @Override
    public Competence modifier(Long id, Edition dto) {
        CompetenceReferentiel c = trouver(id);
        String ancienNom = c.getNom();
        String nouveauNom = dto.getNom().trim();
        if (!ancienNom.equalsIgnoreCase(nouveauNom) && referentielRepository.existsByNomIgnoreCase(nouveauNom))
            throw new ConflitException("La compétence « " + nouveauNom + " » existe déjà.");
        c.setNom(nouveauNom);
        c.setCategorie(vide(dto.getCategorie()));
        c.setDescription(dto.getDescription());
        // Renommage répercuté sur les fiches des membres
        membreCompetenceRepository.findAll().stream().filter(mc -> mc.getIntitule().equalsIgnoreCase(ancienNom))
                .forEach(mc -> {
                    mc.setIntitule(nouveauNom);
                    mc.setCategorie(c.getCategorie());
                });
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, ancienNom.equals(nouveauNom) ? nouveauNom : ancienNom + " → " + nouveauNom);
        return toDTO(referentielRepository.save(c), indexMembres());
    }

    @Override
    public void supprimer(Long id) {
        CompetenceReferentiel c = trouver(id);
        long utilisations = indexMembres().getOrDefault(cle(c.getNom()), List.of()).size();
        if (utilisations > 0)
            throw new ConflitException("Cette compétence est renseignée par " + utilisations + " membre(s) : elle ne peut pas être supprimée.");
        referentielRepository.delete(c);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, c.getNom());
    }

    @Override @Transactional(readOnly = true)
    public List<Detenteur> detenteurs(Long id) {
        CompetenceReferentiel c = trouver(id);
        return indexMembres().getOrDefault(cle(c.getNom()), List.of()).stream()
                .filter(mc -> perimetre.peutAcceder(mc.getMembre().getId()))
                .map(mc -> Detenteur.builder()
                        .membreId(mc.getMembre().getId()).identifiantSnj(mc.getMembre().getIdentifiantSnj())
                        .prenom(mc.getMembre().getPrenom()).nom(mc.getMembre().getNom())
                        .region(mc.getMembre().getLocalisation() != null ? mc.getMembre().getLocalisation().getRegion() : null)
                        .cohorte(mc.getMembre().getCohorte().getNom())
                        .niveau(mc.getNiveau() != null ? mc.getNiveau().name() : null)
                        .build())
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public List<Competence> suggestionsPourMoi(int limite) {
        Long membreId = perimetre.membreIdCourant();
        Set<String> possedees = membreCompetenceRepository.findByMembreId(membreId).stream()
                .map(mc -> cle(mc.getIntitule())).collect(Collectors.toSet());
        Map<String, List<MembreCompetence>> parNom = indexMembres();
        return referentielRepository.findAll().stream()
                .filter(c -> !possedees.contains(cle(c.getNom())))
                .map(c -> toDTO(c, parNom))
                .sorted(Comparator.comparingLong(Competence::getNombreMembres).reversed())
                .limit(Math.max(1, limite))
                .toList();
    }

    @Override
    public int synchroniser() {
        Set<String> connues = referentielRepository.findAll().stream().map(c -> cle(c.getNom())).collect(Collectors.toSet());
        Map<String, MembreCompetence> nouvelles = new LinkedHashMap<>();
        for (MembreCompetence mc : membreCompetenceRepository.findAll()) {
            String k = cle(mc.getIntitule());
            if (!connues.contains(k)) nouvelles.putIfAbsent(k, mc);
        }
        nouvelles.values().forEach(mc -> referentielRepository.save(CompetenceReferentiel.builder()
                .nom(mc.getIntitule().trim()).categorie(vide(mc.getCategorie())).build()));
        return nouvelles.size();
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private Map<String, List<MembreCompetence>> indexMembres() { return indexMembres(null); }

    /** Compétences des membres, regroupées par intitulé ; cohorteId non nul : membres de cette cohorte seulement. */
    private Map<String, List<MembreCompetence>> indexMembres(Long cohorteId) {
        return membreCompetenceRepository.findAll().stream()
                .filter(mc -> cohorteId == null || (mc.getMembre().getCohorte() != null && cohorteId.equals(mc.getMembre().getCohorte().getId())))
                .collect(Collectors.groupingBy(mc -> cle(mc.getIntitule())));
    }

    /** Cohorte du responsable de cohorte connecté (vue par défaut), ou null : toutes les cohortes. */
    private Long cohorteRestreinte(boolean toutes) {
        if (toutes) return null;
        User user;
        try {
            user = perimetre.utilisateurCourant();
        } catch (AccessDeniedException e) {
            return null; // tâche planifiée (rapport mensuel) : aucun utilisateur connecté
        }
        return user.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE ? user.getCohorteId() : null;
    }

    private Competence toDTO(CompetenceReferentiel c, Map<String, List<MembreCompetence>> parNom) {
        List<MembreCompetence> detenteurs = parNom.getOrDefault(cle(c.getNom()), List.of());
        OptionalDouble moyenne = detenteurs.stream().filter(mc -> mc.getNiveau() != null)
                .mapToInt(mc -> niveau(mc.getNiveau())).average();
        return Competence.builder()
                .id(c.getId()).nom(c.getNom()).categorie(categorieDe(c)).description(c.getDescription())
                .nombreMembres(detenteurs.stream().map(mc -> mc.getMembre().getId()).distinct().count())
                .niveauMoyen(moyenne.isPresent() ? Math.round(moyenne.getAsDouble() * 10) / 10.0 : null)
                .anneeAjout(c.getDateCreation() != null ? c.getDateCreation().getYear() : null)
                .build();
    }

    private static int niveau(NiveauCompetenceEnum n) {
        return switch (n) {
            case DEBUTANT -> 1;
            case INTERMEDIAIRE -> 2;
            case AVANCE -> 3;
            case EXPERT -> 4;
        };
    }

    private String categorieDe(CompetenceReferentiel c) {
        return StringUtils.hasText(c.getCategorie()) ? c.getCategorie() : SANS_CATEGORIE;
    }

    private static String cle(String nom) { return nom == null ? "" : nom.trim().toLowerCase(); }

    private static String vide(String s) { return StringUtils.hasText(s) ? s.trim() : null; }

    private CompetenceReferentiel trouver(Long id) {
        return referentielRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Compétence non trouvée : " + id));
    }
}
