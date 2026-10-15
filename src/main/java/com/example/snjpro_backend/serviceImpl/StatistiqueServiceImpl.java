package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.CompetenceReferentielDTOs.Competence;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.dto.StatistiqueDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.*;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.CompetenceReferentielServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.StatistiqueServiceInterface;
import com.example.snjpro_backend.specification.MembreSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StatistiqueServiceImpl implements StatistiqueServiceInterface {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<int[]> TRANCHES = List.of(new int[]{18, 20}, new int[]{21, 23}, new int[]{24, 26},
            new int[]{27, 29}, new int[]{30, 32}, new int[]{33, 35}, new int[]{36, 200});

    private final MembreRepository membreRepository;
    private final CohorteRepository cohorteRepository;
    private final MobilisationRepository mobilisationRepository;
    private final ParticipationMobilisationRepository participationRepository;
    private final HistoriqueRepository historiqueRepository;
    private final RapportRepository rapportRepository;
    private final CompetenceReferentielServiceInterface competenceService;
    private final GenerateurFichiers generateur;
    private final StockageFichierService stockage;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Tableau de bord
    // =====================================================================

    @Override
    public TableauDeBord tableauDeBord() {
        User u = perimetre.utilisateurCourant();
        boolean responsable = perimetre.estResponsable(u);
        List<Membre> membres = membresDuPerimetre();
        Set<Long> idsMembres = membres.stream().map(Membre::getId).collect(Collectors.toSet());
        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long nouveauxCeMois = membres.stream().filter(m -> m.getDateCreation() != null && m.getDateCreation().isAfter(debutMois)).count();
        long avantMois = membres.size() - nouveauxCeMois;
        long complets = membres.stream().filter(CompletudeProfil::complet).count();
        long mobilisables = membres.stream().filter(m -> m.getStatut() == StatutMembreEnum.ACTIF
                && !Boolean.FALSE.equals(m.getDisponibleMobilisation())).count();
        long dispoSemaine = participationRepository.findByDateSollicitationAfter(LocalDateTime.now().minusDays(30)).stream()
                .filter(p -> p.getReponse() == ReponseMobilisation.DISPONIBLE && p.getDateReponse() != null
                        && p.getDateReponse().isAfter(LocalDateTime.now().minusDays(7)))
                .map(p -> p.getMembre().getId()).filter(idsMembres::contains).distinct().count();
        // Responsables : cohortes où se trouvent leurs membres (le responsable de cohorte voit toujours la sienne)
        Set<Long> idsCohortes = membres.stream().map(m -> m.getCohorte().getId()).collect(Collectors.toSet());
        if (u.getCohorteId() != null && u.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE) idsCohortes.add(u.getCohorteId());
        List<Cohorte> cohortes = cohorteRepository.findAll().stream().filter(c -> !responsable || idsCohortes.contains(c.getId())).toList();
        // Compétences les plus présentes parmi les membres du périmètre
        List<Libelle> parCompetence = membres.stream().flatMap(m -> m.getCompetences().stream().map(c -> c.getIntitule().trim()).distinct())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Libelle(e.getKey(), e.getValue(), pourcentage(e.getValue(), membres.size()))).toList();
        int anneeCourante = LocalDate.now().getYear();
        Map<Integer, Long> evolution = new TreeMap<>();
        for (int a = anneeCourante - 10; a <= anneeCourante; a++) evolution.put(a, 0L);
        membres.forEach(m -> {
            Integer a = anneeIncorporation(m);
            if (a != null && evolution.containsKey(a)) evolution.merge(a, 1L, Long::sum);
        });
        return TableauDeBord.builder()
                .membresEnregistres(Kpi.builder().valeur(membres.size())
                        .evolutionPourcentage(avantMois == 0 ? null : Math.round(nouveauxCeMois * 1000.0 / avantMois) / 10.0)
                        .detail(nouveauxCeMois + " nouveau(x) ce mois-ci").build())
                .cohortes(cohortes.size())
                .cohortesParStatut(cohortes.stream().collect(Collectors.groupingBy(c -> c.getStatut().name(), TreeMap::new, Collectors.counting())))
                .pourcentageProfilsComplets(pourcentage(complets, membres.size()))
                .profilsComplets(complets)
                .membresMobilisables(Kpi.builder().valeur(mobilisables).detail(dispoSemaine + " disponible(s) cette semaine").build())
                .disponiblesCetteSemaine(dispoSemaine)
                .evolutionEffectifs(evolution)
                .repartitionSexe(repartition(membres, m -> m.getSexe() == SexeEnum.FEMININ ? "Femmes" : "Hommes"))
                .repartitionRegions(repartition(membres, StatistiqueServiceImpl::region))
                .topCompetences(parCompetence.stream().limit(6).toList())
                .nombreCompetences(parCompetence.size())
                // Responsables : uniquement leurs propres actions
                .activiteRecente(historiqueRepository.findAll(PageRequest.of(0, responsable ? 200 : 6, Sort.by(Sort.Direction.DESC, "dateAction"))).stream()
                        .filter(h -> !h.getAction().startsWith("CONNEXION") && !h.getAction().equals("DECONNEXION"))
                        .filter(h -> !responsable || u.getUsername().equals(h.getUtilisateur()))
                        .limit(5)
                        .map(h -> ActiviteRecente.builder().titre(h.getDetails() != null ? h.getDetails() : h.getEntite())
                                .description(libelleAction(h.getAction()) + " · " + h.getEntite().toLowerCase())
                                .type(h.getAction()).utilisateur(h.getUtilisateur()).entite(h.getEntite())
                                .entiteId(h.getEntiteId()).date(h.getDateAction()).build())
                        .toList())
                .build();
    }

    // =====================================================================
    // Statistiques détaillées
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public Statistiques statistiques(Filtres f) {
        List<Membre> membres = filtrer(f);
        List<Membre> avecSituation = membres.stream().filter(m -> m.getSituationProfessionnelle() != null).toList();
        Map<Integer, List<Membre>> parAnnee = membres.stream().filter(m -> anneeIncorporation(m) != null)
                .collect(Collectors.groupingBy(StatistiqueServiceImpl::anneeIncorporation, TreeMap::new, Collectors.toList()));
        Map<Integer, Integer> completude = new TreeMap<>();
        parAnnee.forEach((a, l) -> completude.put(a, (int) Math.round(l.stream().mapToInt(CompletudeProfil::taux).average().orElse(0))));
        return Statistiques.builder()
                .totalMembres(membres.size())
                .repartitionGeographique(repartition(membres, StatistiqueServiceImpl::region))
                .pyramideAges(pyramide(membres))
                .situationProfessionnelle(repartition(avecSituation, m -> libelleStatut(m.getSituationProfessionnelle().getStatut())))
                .profilsAvecSituation(avecSituation.size())
                .completudeParAnnee(completude)
                .repartitionSexe(repartition(membres, m -> m.getSexe() == SexeEnum.FEMININ ? "Femmes" : "Hommes"))
                .parCohorte(repartition(membres, m -> m.getCohorte().getNom()))
                .build();
    }

    @Override @Transactional(readOnly = true)
    public byte[] exporterStatistiques(Filtres f, FormatFichier format) {
        Statistiques s = statistiques(f);
        List<List<String>> lignes = new ArrayList<>();
        s.getRepartitionGeographique().forEach(l -> lignes.add(List.of("Région", l.libelle(), String.valueOf(l.valeur()), l.pourcentage() + " %")));
        s.getRepartitionSexe().forEach(l -> lignes.add(List.of("Sexe", l.libelle(), String.valueOf(l.valeur()), l.pourcentage() + " %")));
        s.getSituationProfessionnelle().forEach(l -> lignes.add(List.of("Situation professionnelle", l.libelle(), String.valueOf(l.valeur()), l.pourcentage() + " %")));
        s.getPyramideAges().forEach(t -> lignes.add(List.of("Âge", t.getTranche(), t.getHommes() + " H / " + t.getFemmes() + " F", "")));
        s.getCompletudeParAnnee().forEach((a, v) -> lignes.add(List.of("Complétude", String.valueOf(a), v + " %", "")));
        historiqueService.enregistrer("EXPORT", "STATISTIQUES", null, format.name());
        return generateur.tableau(new GenerateurFichiers.Tableau("Statistiques SNJPro (" + s.getTotalMembres() + " membres)",
                List.of("Indicateur", "Valeur", "Effectif", "Part"), lignes), format);
    }

    // =====================================================================
    // Rapports
    // =====================================================================

    @Override
    public RapportDTO genererRapport(DemandeRapport d) {
        return toRapport(generer(d, perimetreUtilisateur(), false));
    }

    @Override @Transactional(readOnly = true)
    public List<RapportDTO> rapports() {
        return rapportRepository.findAllByOrderByDateGenerationDesc().stream().map(this::toRapport).toList();
    }

    @Override @Transactional(readOnly = true)
    public Fichier telechargerRapport(Long id) {
        Rapport r = rapportRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Rapport non trouvé : " + id));
        return new Fichier(stockage.lire(r.getChemin()),
                r.getTitre().replaceAll("[^\\p{L}\\p{N}]+", "_") + "." + GenerateurFichiers.extension(r.getFormat()),
                GenerateurFichiers.typeMime(r.getFormat()));
    }

    @Override
    public void supprimerRapport(Long id) {
        Rapport r = rapportRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Rapport non trouvé : " + id));
        stockage.supprimer(r.getChemin());
        rapportRepository.delete(r);
    }

    /** Rapports mensuels générés automatiquement le 1er de chaque mois à 6 h. */
    @Scheduled(cron = "0 0 6 1 * *")
    public void rapportsMensuels() {
        LocalDate fin = LocalDate.now().minusDays(1);
        LocalDate debut = fin.withDayOfMonth(1);
        for (Object[] r : List.of(new Object[]{TypeRapport.EFFECTIFS_COHORTES, FormatFichier.PDF},
                new Object[]{TypeRapport.COMPETENCES, FormatFichier.XLSX}, new Object[]{TypeRapport.PROFILS_INCOMPLETS, FormatFichier.XLSX})) {
            DemandeRapport d = new DemandeRapport();
            d.setType((TypeRapport) r[0]);
            d.setFormat((FormatFichier) r[1]);
            d.setDu(debut);
            d.setAu(fin);
            try {
                generer(d, "system", true);
            } catch (Exception e) {
                log.error("Échec de la génération automatique du rapport {} : {}", r[0], e.getMessage());
            }
        }
    }

    // =====================================================================
    // Export des membres
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public EstimationExport estimerExport(Export export) {
        long n = membresExport(export).size();
        return EstimationExport.builder().nombreMembres(n).tailleEstimeeOctets(n * 300 + 2048).build();
    }

    @Override @Transactional(readOnly = true)
    public Fichier exporterMembres(Export export) {
        List<Membre> membres = membresExport(export);
        Set<String> s = new HashSet<>(export.getSections());
        List<String> entetes = new ArrayList<>(List.of("Identifiant"));
        if (s.contains("IDENTITE")) entetes.addAll(List.of("Prénom", "Nom", "Sexe", "Date de naissance", "Lieu de naissance"));
        if (s.contains("CONTACT")) entetes.addAll(List.of("Téléphone", "E-mail"));
        if (s.contains("COHORTE")) entetes.addAll(List.of("Cohorte", "Statut"));
        if (s.contains("LOCALISATION")) entetes.addAll(List.of("Région", "Cercle", "Commune"));
        if (s.contains("SITUATION")) entetes.addAll(List.of("Situation", "Profession", "Employeur"));
        if (s.contains("COMPETENCES")) entetes.add("Compétences");
        boolean masquer = export.isMasquerDonneesSensibles();
        List<List<String>> lignes = membres.stream().map(m -> {
            List<String> l = new ArrayList<>(List.of(m.getIdentifiantSnj()));
            if (s.contains("IDENTITE")) l.addAll(List.of(m.getPrenom(), m.getNom(), m.getSexe().name(),
                    m.getDateNaissance() == null ? "" : m.getDateNaissance().format(DATE), nvl(m.getLieuNaissance())));
            if (s.contains("CONTACT")) l.addAll(List.of(masquer ? masquerTel(m.getNumeroTelephone()) : nvl(m.getNumeroTelephone()),
                    masquer ? masquerEmail(m.getEmail()) : nvl(m.getEmail())));
            if (s.contains("COHORTE")) l.addAll(List.of(m.getCohorte().getNom(), m.getStatut().name()));
            if (s.contains("LOCALISATION")) {
                Localisation loc = m.getLocalisation();
                l.addAll(List.of(loc == null ? "" : nvl(loc.getRegion()), loc == null ? "" : nvl(loc.getCercle()), loc == null ? "" : nvl(loc.getCommune())));
            }
            if (s.contains("SITUATION")) {
                SituationProfessionnelle sp = m.getSituationProfessionnelle();
                l.addAll(List.of(sp == null ? "" : libelleStatut(sp.getStatut()), sp == null ? "" : nvl(sp.getProfession()),
                        sp == null ? "" : nvl(sp.getNomEmployeur())));
            }
            if (s.contains("COMPETENCES")) l.add(m.getCompetences().stream()
                    .map(c -> c.getIntitule() + (c.getNiveau() != null ? " (" + c.getNiveau().name().toLowerCase() + ")" : ""))
                    .collect(Collectors.joining(", ")));
            return l;
        }).toList();
        historiqueService.enregistrer("EXPORT", "MEMBRE", null, membres.size() + " membre(s) · " + export.getFormat()
                + (masquer ? " · données sensibles masquées" : " · données complètes"));
        byte[] contenu = generateur.tableau(new GenerateurFichiers.Tableau("Membres SNJ", entetes, lignes), export.getFormat());
        return new Fichier(contenu, "membres_" + LocalDate.now() + "." + GenerateurFichiers.extension(export.getFormat()),
                GenerateurFichiers.typeMime(export.getFormat()));
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private Rapport generer(DemandeRapport d, String auteur, boolean automatique) {
        LocalDate du = d.getDu(), au = d.getAu();
        if (du != null && au != null && au.isBefore(du)) throw new IllegalArgumentException("La date de fin doit suivre la date de début.");
        List<Membre> membres = membresDuPerimetreOuTous(automatique).stream()
                .filter(m -> !StringUtils.hasText(d.getRegion()) || d.getRegion().equalsIgnoreCase(region(m))).toList();
        String periode = (du == null ? "…" : du.format(DATE)) + " – " + (au == null ? "aujourd'hui" : au.format(DATE));
        GenerateurFichiers.Tableau tableau = switch (d.getType()) {
            case EFFECTIFS_COHORTES, STATISTIQUES_COHORTES -> {
                Map<Cohorte, List<Membre>> parCohorte = membres.stream().collect(Collectors.groupingBy(Membre::getCohorte));
                List<List<String>> l = cohorteRepository.findAll().stream().sorted(Comparator.comparing(Cohorte::getNom)).map(c -> {
                    List<Membre> mc = parCohorte.getOrDefault(c, List.of());
                    return List.of(c.getNom(), c.getStatut().name(), nvl(c.getRecruesInitiales()), String.valueOf(mc.size()),
                            String.valueOf(mc.stream().filter(m -> m.getStatut() == StatutMembreEnum.ACTIF).count()),
                            String.valueOf(mc.stream().filter(m -> m.getSexe() == SexeEnum.FEMININ).count()),
                            pourcentage(mc.stream().filter(CompletudeProfil::complet).count(), mc.size()) + " %");
                }).toList();
                yield new GenerateurFichiers.Tableau("Effectifs par cohorte", List.of("Cohorte", "Statut", "Recrues", "Membres", "Actifs", "Femmes", "Profils complets"), l);
            }
            case COMPETENCES -> new GenerateurFichiers.Tableau("Répartition par compétences", List.of("Compétence", "Catégorie", "Membres", "Niveau moyen /4"),
                    competenceService.lister(null, null, "MEMBRES").stream()
                            .map(c -> List.of(c.getNom(), c.getCategorie(), String.valueOf(c.getNombreMembres()),
                                    c.getNiveauMoyen() == null ? "" : String.valueOf(c.getNiveauMoyen()))).toList());
            case MOBILISATIONS -> new GenerateurFichiers.Tableau("Bilan des mobilisations", List.of("Mobilisation", "Début", "Statut", "Sollicités", "Disponibles", "Indisponibles"),
                    mobilisationRepository.findAllByOrderByDateCreationDesc().stream()
                            .filter(m -> (du == null || !m.getDateDebut().isBefore(du)) && (au == null || !m.getDateDebut().isAfter(au)))
                            .filter(m -> !StringUtils.hasText(d.getRegion()) || d.getRegion().equalsIgnoreCase(m.getRegion()))
                            .map(m -> List.of(m.getTitre(), m.getDateDebut().format(DATE), m.getStatut().name(),
                                    String.valueOf(participationRepository.countByMobilisationId(m.getId())),
                                    String.valueOf(participationRepository.countByMobilisationIdAndReponse(m.getId(), ReponseMobilisation.DISPONIBLE)),
                                    String.valueOf(participationRepository.countByMobilisationIdAndReponse(m.getId(), ReponseMobilisation.INDISPONIBLE))))
                            .toList());
            case PROFILS_INCOMPLETS -> new GenerateurFichiers.Tableau("Profils incomplets", List.of("Identifiant", "Membre", "Cohorte", "Région", "Complétude", "Éléments manquants"),
                    membres.stream().filter(m -> !CompletudeProfil.complet(m))
                            .sorted(Comparator.comparingInt(CompletudeProfil::taux))
                            .map(m -> List.of(m.getIdentifiantSnj(), m.getPrenom() + " " + m.getNom(), m.getCohorte().getNom(),
                                    nvl(region(m)), CompletudeProfil.taux(m) + " %", String.join(", ", CompletudeProfil.manquants(m))))
                            .toList());
        };
        String titre = tableau.titre() + (d.getRegion() != null ? " — " + d.getRegion() : "") + " (" + periode + ")";
        byte[] contenu = generateur.tableau(new GenerateurFichiers.Tableau(titre, tableau.entetes(), tableau.lignes()), d.getFormat());
        Rapport r = rapportRepository.save(Rapport.builder()
                .titre(tableau.titre() + (d.getRegion() != null ? " — " + d.getRegion() : ""))
                .type(d.getType()).format(d.getFormat()).dateDebut(du).dateFin(au).region(d.getRegion())
                .chemin(stockage.ecrire(contenu, "rapports", GenerateurFichiers.extension(d.getFormat())))
                .taille(contenu.length).generePar(auteur).automatique(automatique).build());
        historiqueService.enregistrer("GENERATION", "RAPPORT", r.getId(), r.getTitre(), auteur);
        return r;
    }

    private List<Membre> membresExport(Export export) {
        MembreFilterDTO f = export.getFiltres() != null ? export.getFiltres() : new MembreFilterDTO();
        perimetre.restreindre(f);
        return membreRepository.findAll(MembreSpecification.avecFiltres(f));
    }

    private List<Membre> filtrer(Filtres f) {
        return membresDuPerimetre().stream()
                .filter(m -> f.annee() == null || Objects.equals(anneeIncorporation(m), f.annee()))
                .filter(m -> f.cohorteId() == null || m.getCohorte().getId().equals(f.cohorteId()))
                .filter(m -> !StringUtils.hasText(f.region()) || f.region().equalsIgnoreCase(region(m)))
                .filter(m -> f.sexe() == null || m.getSexe() == f.sexe())
                .toList();
    }

    /** Membres non archivés visibles par l'utilisateur courant (région / cohorte pour les responsables). */
    private List<Membre> membresDuPerimetre() {
        User u = perimetre.utilisateurCourant();
        return membreRepository.findAllNonArchives().stream()
                .filter(m -> switch (u.getRole()) {
                    case ROLE_RESPONSABLE_REGIONAL -> u.getRegion() != null && u.getRegion().equalsIgnoreCase(region(m));
                    case ROLE_RESPONSABLE_COHORTE -> Objects.equals(u.getCohorteId(), m.getCohorte().getId());
                    default -> true;
                })
                .toList();
    }

    private List<Membre> membresDuPerimetreOuTous(boolean automatique) {
        return automatique ? membreRepository.findAllNonArchives() : membresDuPerimetre();
    }

    private String perimetreUtilisateur() { return perimetre.utilisateurCourant().getUsername(); }

    private static List<TrancheAge> pyramide(List<Membre> membres) {
        LocalDate aujourdhui = LocalDate.now();
        List<TrancheAge> tranches = new ArrayList<>();
        for (int i = TRANCHES.size() - 1; i >= 0; i--) {
            int[] t = TRANCHES.get(i);
            long h = 0, f = 0;
            for (Membre m : membres) {
                if (m.getDateNaissance() == null) continue;
                int age = Period.between(m.getDateNaissance(), aujourdhui).getYears();
                if (age < t[0] || age > t[1]) continue;
                if (m.getSexe() == SexeEnum.FEMININ) f++; else h++;
            }
            tranches.add(TrancheAge.builder().tranche(t[1] >= 200 ? t[0] + " +" : t[0] + " – " + t[1]).hommes(h).femmes(f)
                    .pourcentageHommes(membres.isEmpty() ? 0 : Math.round(h * 1000.0 / membres.size()) / 10.0)
                    .pourcentageFemmes(membres.isEmpty() ? 0 : Math.round(f * 1000.0 / membres.size()) / 10.0).build());
        }
        return tranches;
    }

    private static List<Libelle> repartition(List<Membre> membres, Function<Membre, String> cle) {
        Map<String, Long> m = membres.stream().collect(Collectors.groupingBy(x -> Objects.toString(cle.apply(x), "Non renseigné"), Collectors.counting()));
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Libelle(e.getKey(), e.getValue(), pourcentage(e.getValue(), membres.size()))).toList();
    }

    static Integer anneeIncorporation(Membre m) {
        if (m.getDateIncorporation() != null) return m.getDateIncorporation().getYear();
        if (m.getCohorte().getAnneeDebut() != null) return m.getCohorte().getAnneeDebut();
        if (m.getCohorte().getDateDebut() != null) return m.getCohorte().getDateDebut().getYear();
        return m.getDateCreation() != null ? m.getDateCreation().getYear() : null;
    }

    static String region(Membre m) {
        return m.getLocalisation() != null ? m.getLocalisation().getRegion() : null;
    }

    static String libelleStatut(StatutProfessionnelEnum s) {
        if (s == null) return "Non renseigné";
        return switch (s) {
            case EMPLOYE -> "Salariés";
            case AUTO_EMPLOYE -> "Entrepreneurs";
            case CHOMEUR -> "Sans emploi";
            case ETUDIANT -> "Étudiants";
            case RETRAITE -> "Retraités";
            case AUTRE -> "Autres";
        };
    }

    private static String libelleAction(String action) {
        return switch (action) {
            case "CREATION" -> "Création";
            case "MODIFICATION" -> "Modification";
            case "IMPORT" -> "Import";
            case "SUPPRESSION" -> "Suppression";
            case "EXPORT" -> "Export";
            case "ENVOI" -> "Envoi";
            default -> action.charAt(0) + action.substring(1).toLowerCase().replace('_', ' ');
        };
    }

    private static String masquerTel(String t) {
        if (!StringUtils.hasText(t) || t.length() < 6) return nvl(t);
        return t.substring(0, t.length() - 6) + " •• •• " + t.substring(t.length() - 2);
    }

    private static String masquerEmail(String e) {
        if (!StringUtils.hasText(e) || !e.contains("@")) return nvl(e);
        return e.charAt(0) + "•••" + e.substring(e.indexOf('@'));
    }

    private static int pourcentage(long partie, long total) {
        return total == 0 ? 0 : (int) Math.round(partie * 100.0 / total);
    }

    private static String nvl(Object o) { return o == null ? "" : o.toString(); }

    private RapportDTO toRapport(Rapport r) {
        return RapportDTO.builder()
                .id(r.getId()).titre(r.getTitre()).type(r.getType()).format(r.getFormat())
                .periode(r.getDateDebut() == null && r.getDateFin() == null ? null
                        : (r.getDateDebut() == null ? "…" : r.getDateDebut().format(DATE)) + " – " + (r.getDateFin() == null ? "…" : r.getDateFin().format(DATE)))
                .region(r.getRegion()).taille(r.getTaille()).generePar(r.getGenerePar()).dateGeneration(r.getDateGeneration())
                .automatique(r.isAutomatique()).urlTelechargement("/api/rapports/" + r.getId() + "/telecharger")
                .build();
    }
}
