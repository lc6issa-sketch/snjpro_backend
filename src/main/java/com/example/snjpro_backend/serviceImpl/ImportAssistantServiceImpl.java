package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.ImportAssistantDTOs.*;
import com.example.snjpro_backend.dto.ImportResultDTO.ImportErreurDTO;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.DecisionDoublon;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import com.example.snjpro_backend.enumeration.Enumerations.StatutImport;
import com.example.snjpro_backend.enumeration.RoleEnum;
import org.springframework.security.access.AccessDeniedException;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.repository.ImportSessionRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.ImportAssistantServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ImportAssistantServiceImpl implements ImportAssistantServiceInterface {

    private static final String ENTITE = "IMPORT";
    private static final int MAX_LIGNES = 5000;
    private static final Map<String, String> LIBELLES = new LinkedHashMap<>();
    static {
        LIBELLES.put("prenom", "Prénom");
        LIBELLES.put("nom", "Nom");
        LIBELLES.put("sexe", "Sexe");
        LIBELLES.put("dateNaissance", "Date de naissance");
        LIBELLES.put("lieuNaissance", "Lieu de naissance");
        LIBELLES.put("telephone", "Téléphone");
        LIBELLES.put("email", "Email");
        LIBELLES.put("region", "Région");
        LIBELLES.put("cercle", "Cercle");
        LIBELLES.put("commune", "Commune");
        LIBELLES.put("quartier", "Quartier");
        LIBELLES.put("profession", "Profession");
        LIBELLES.put("statutProfessionnel", "Statut professionnel");
        LIBELLES.put("secteurActivite", "Secteur d'activité");
        LIBELLES.put("employeur", "Employeur");
        LIBELLES.put("competences", "Compétences");
    }

    private final ImportSessionRepository sessionRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreRepository membreRepository;
    private final RechercheImportServiceImpl importeur;
    private final LecteurFichierTabulaire lecteur;
    private final StockageFichierService stockage;
    private final GenerateurFichiers generateur;
    private final HistoriqueServiceInterface historiqueService;
    private final NotificationServiceInterface notificationService;
    private final PerimetreService perimetre;
    private final ObjectMapper objectMapper;

    /** Ligne du fichier après application de la correspondance des colonnes. */
    private record LigneAnalysee(int numero, RechercheImportServiceImpl.LigneMembre membre, List<ErreurLigne> erreurs,
                                 List<String> motifsFichier, Membre existant, List<String> motifsExistant) {
        boolean valide() { return erreurs.isEmpty(); }
        boolean doublon() { return valide() && (existant != null || !motifsFichier.isEmpty()); }
    }

    // =====================================================================
    // Étapes 1-2 : dépôt, analyse et correspondance des colonnes
    // =====================================================================

    @Override
    public Analyse analyser(MultipartFile fichier, Long cohorteId) {
        if (fichier == null || fichier.isEmpty())
            throw new IllegalArgumentException("Aucun fichier fourni ou fichier vide.");
        perimetre.exigerCohorte(cohorteId);
        Cohorte cohorte = cohorte(cohorteId);
        if (cohorte.getStatut() == StatutCohorteEnum.ARCHIVEE)
            throw new ConflitException("Impossible d'importer dans une cohorte archivée.");
        LecteurFichierTabulaire.Tableau tableau;
        try {
            tableau = lecteur.lire(fichier);
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible : " + e.getMessage());
        }
        if (tableau.entetes().isEmpty()) throw new IllegalArgumentException("Le fichier ne contient aucune colonne.");
        if (tableau.lignes().size() > MAX_LIGNES)
            throw new IllegalArgumentException("Le fichier dépasse " + MAX_LIGNES + " lignes : découpez-le en plusieurs fichiers.");

        Map<String, String> correspondance = new LinkedHashMap<>();
        Set<String> dejaPris = new HashSet<>();
        for (String entete : tableau.entetes()) {
            if (!StringUtils.hasText(entete)) continue;
            String champ = importeur.colonnePour(entete);
            if (champ == null) champ = colonneProbable(entete);
            correspondance.put(entete, champ != null && dejaPris.add(champ) ? champ : null);
        }
        ImportSession session = ImportSession.builder()
                .nomFichier(fichier.getOriginalFilename() == null ? "import.csv" : fichier.getOriginalFilename())
                .chemin(stockage.enregistrer(fichier, "imports"))
                .taille(fichier.getSize())
                .cohorteId(cohorteId)
                .correspondance(json(correspondance))
                .decisions("{}")
                .totalLignes((int) tableau.lignes().stream().filter(l -> l.valeurs().stream().anyMatch(StringUtils::hasText)).count())
                .creePar(perimetre.utilisateurCourant().getUsername())
                .build();
        session = sessionRepository.save(session);
        historiqueService.enregistrer("ANALYSE_IMPORT", ENTITE, session.getId(), session.getNomFichier());
        return analyse(session, tableau);
    }

    @Override @Transactional(readOnly = true)
    public Page<Session> lister(Pageable pageable) {
        User user = perimetre.utilisateurCourant();
        Page<ImportSession> sessions = user.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE
                ? sessionRepository.findByCohorteIdOrderByDateCreationDesc(user.getCohorteId(), pageable)
                : sessionRepository.findAllByOrderByDateCreationDesc(pageable);
        return sessions.map(this::toSession);
    }

    @Override @Transactional(readOnly = true)
    public Analyse obtenir(Long id) {
        ImportSession s = session(id);
        return analyse(s, lire(s));
    }

    @Override
    public Analyse definirCorrespondance(Long id, Map<String, String> correspondance) {
        ImportSession s = modifiable(id);
        LecteurFichierTabulaire.Tableau tableau = lire(s);
        Map<String, String> nouvelle = new LinkedHashMap<>();
        Set<String> dejaPris = new HashSet<>();
        for (String entete : tableau.entetes()) {
            if (!StringUtils.hasText(entete)) continue;
            String champ = correspondance == null ? null : correspondance.get(entete);
            if (!StringUtils.hasText(champ)) { nouvelle.put(entete, null); continue; }
            if (!LIBELLES.containsKey(champ))
                throw new IllegalArgumentException("Champ inconnu : " + champ + " (colonne « " + entete + " »)");
            if (!dejaPris.add(champ))
                throw new IllegalArgumentException("Le champ « " + LIBELLES.get(champ) + " » est associé à plusieurs colonnes.");
            nouvelle.put(entete, champ);
        }
        s.setCorrespondance(json(nouvelle));
        s.setDecisions("{}");
        s.setStatut(StatutImport.ANALYSE);
        return analyse(sessionRepository.save(s), tableau);
    }

    // =====================================================================
    // Étape 3 : vérification (erreurs + doublons)
    // =====================================================================

    @Override
    public Verification verifier(Long id) {
        ImportSession s = modifiable(id);
        exigerObligatoires(s);
        Verification v = verification(s, analyserLignes(s));
        s.setStatut(StatutImport.VERIFIE);
        s.setErreurs(v.getLignesEnErreur());
        s.setDoublons(v.getDoublonsDetectes());
        v.setSession(toSession(sessionRepository.save(s)));
        return v;
    }

    @Override
    public DoublonLigne decider(Long id, int ligne, DecisionDoublon decision) {
        if (decision == null) throw new IllegalArgumentException("La décision est obligatoire.");
        ImportSession s = modifiable(id);
        LigneAnalysee l = analyserLignes(s).stream().filter(x -> x.numero() == ligne && x.doublon()).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Aucun doublon à la ligne " + ligne));
        if (decision == DecisionDoublon.FUSIONNER && l.existant() == null)
            throw new IllegalArgumentException("Fusion impossible : ce doublon est interne au fichier (aucune fiche existante).");
        if (decision == DecisionDoublon.FUSIONNER && !peutFusionner(l.existant()))
            throw new AccessDeniedException("Fusion impossible : la fiche existante n'appartient pas à votre cohorte.");
        Map<String, DecisionDoublon> decisions = decisions(s);
        decisions.put(String.valueOf(ligne), decision);
        s.setDecisions(json(decisions));
        sessionRepository.save(s);
        return doublon(l, decision);
    }

    @Override
    public Verification deciderTous(Long id, DecisionDoublon decision) {
        if (decision == null) throw new IllegalArgumentException("La décision est obligatoire.");
        ImportSession s = modifiable(id);
        List<LigneAnalysee> lignes = analyserLignes(s);
        Map<String, DecisionDoublon> decisions = decisions(s);
        for (LigneAnalysee l : lignes) {
            if (!l.doublon()) continue;
            DecisionDoublon d = decision == DecisionDoublon.FUSIONNER && !peutFusionner(l.existant()) ? DecisionDoublon.IGNORER : decision;
            decisions.put(String.valueOf(l.numero()), d);
        }
        s.setDecisions(json(decisions));
        sessionRepository.save(s);
        return verification(s, lignes);
    }

    // =====================================================================
    // Étape 4 : import
    // =====================================================================

    @Override
    public Resultat importer(Long id) {
        ImportSession s = modifiable(id);
        exigerObligatoires(s);
        Cohorte cohorte = cohorte(s.getCohorteId());
        if (cohorte.getStatut() == StatutCohorteEnum.ARCHIVEE)
            throw new ConflitException("Impossible d'importer dans une cohorte archivée.");
        String auteur = perimetre.utilisateurCourant().getUsername();
        Map<String, DecisionDoublon> decisions = decisions(s);
        List<String> crees = new ArrayList<>();
        List<String> fusionnes = new ArrayList<>();
        List<ErreurLigne> erreurs = new ArrayList<>();
        int ignores = 0, doublons = 0, enErreur = 0;
        for (LigneAnalysee l : analyserLignes(s)) {
            if (!l.valide()) { erreurs.addAll(l.erreurs()); ignores++; enErreur++; continue; }
            if (l.doublon()) {
                doublons++;
                // doublon sans décision : ignoré par prudence
                DecisionDoublon d = decisions.getOrDefault(String.valueOf(l.numero()), DecisionDoublon.IGNORER);
                if (d == DecisionDoublon.IGNORER) { ignores++; continue; }
                if (d == DecisionDoublon.FUSIONNER && !peutFusionner(l.existant())) { ignores++; continue; }
                if (d == DecisionDoublon.FUSIONNER) {
                    fusionner(l.existant(), l.membre(), auteur);
                    fusionnes.add(l.existant().getIdentifiantSnj());
                    continue;
                }
            }
            crees.add(importeur.enregistrer(l.membre(), cohorte, auteur));
        }
        s.setStatut(StatutImport.TERMINE);
        s.setImportes(crees.size());
        s.setFusionnes(fusionnes.size());
        s.setIgnores(ignores);
        s.setErreurs(enErreur);
        s.setDoublons(doublons);
        s.setDateImport(LocalDateTime.now());
        String message = String.format("Import terminé : %d membre(s) créé(s), %d fiche(s) fusionnée(s), %d ligne(s) ignorée(s) dont %d en erreur.",
                crees.size(), fusionnes.size(), ignores, enErreur);
        Resultat r = Resultat.builder().identifiantsCrees(crees).identifiantsFusionnes(fusionnes).erreurs(erreurs).message(message).build();
        s.setResultat(json(r));
        s = sessionRepository.save(s);
        r.setSession(toSession(s));
        historiqueService.enregistrer("IMPORT", "COHORTE", cohorte.getId(), s.getNomFichier() + " - " + message);
        notificationService.alerterRoles(List.of(RoleEnum.ROLE_ADMIN_SYSTEME, RoleEnum.ROLE_ADMIN_SNJ), null,
                "Import terminé : " + s.getNomFichier(), message + " Cohorte : " + cohorte.getNom(), "IMPORT", ENTITE, s.getId());
        return r;
    }

    @Override @Transactional(readOnly = true)
    public Fichier exporterErreurs(Long id, FormatFichier format) {
        ImportSession s = session(id);
        List<List<String>> lignes = new ArrayList<>();
        for (LigneAnalysee l : analyserLignes(s)) {
            for (ErreurLigne e : l.erreurs())
                lignes.add(List.of(String.valueOf(e.getLigne()), libelle(e.getChamp()), nvl(e.getValeur()), e.getMessage(), nvl(e.getSuggestion())));
            if (l.doublon())
                lignes.add(List.of(String.valueOf(l.numero()), "Doublon", l.membre().prenom + " " + l.membre().nom,
                        String.join(" ; ", tousMotifs(l)), "Choisir : ignorer, fusionner ou conserver les deux"));
        }
        byte[] contenu = generateur.tableau(new GenerateurFichiers.Tableau("Erreurs d'import — " + s.getNomFichier(),
                List.of("Ligne", "Champ", "Valeur", "Problème", "Suggestion"), lignes), format);
        return new Fichier(contenu, "erreurs_import_" + s.getId() + "." + GenerateurFichiers.extension(format),
                GenerateurFichiers.typeMime(format));
    }

    @Override @Transactional(readOnly = true)
    public Fichier rapport(Long id) {
        ImportSession s = session(id);
        String cohorteNom = cohorteRepository.findById(s.getCohorteId()).map(Cohorte::getNom).orElse("—");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<GenerateurFichiers.Section> sections = new ArrayList<>();
        sections.add(new GenerateurFichiers.Section("Fichier", List.of(
                new String[]{"Nom", s.getNomFichier()},
                new String[]{"Cohorte", cohorteNom},
                new String[]{"Déposé par", s.getCreePar()},
                new String[]{"Déposé le", s.getDateCreation().format(f)},
                new String[]{"Importé le", s.getDateImport() == null ? "Non importé" : s.getDateImport().format(f)},
                new String[]{"Statut", s.getStatut().name()})));
        sections.add(new GenerateurFichiers.Section("Résultat", List.of(
                new String[]{"Lignes du fichier", String.valueOf(s.getTotalLignes())},
                new String[]{"Membres créés", String.valueOf(s.getImportes())},
                new String[]{"Fiches fusionnées", String.valueOf(s.getFusionnes())},
                new String[]{"Lignes ignorées", String.valueOf(s.getIgnores())},
                new String[]{"Lignes en erreur", String.valueOf(s.getErreurs())},
                new String[]{"Doublons détectés", String.valueOf(s.getDoublons())})));
        if (s.getResultat() != null) {
            Resultat r = lireJson(s.getResultat(), new TypeReference<Resultat>() { });
            List<String[]> erreurs = new ArrayList<>();
            r.getErreurs().stream().limit(200).forEach(e -> erreurs.add(new String[]{"Ligne " + e.getLigne(),
                    libelle(e.getChamp()) + " — " + e.getMessage()
                            + (StringUtils.hasText(e.getValeur()) ? " (« " + e.getValeur() + " »)" : "")}));
            if (!erreurs.isEmpty()) sections.add(new GenerateurFichiers.Section("Erreurs", erreurs));
            if (!r.getIdentifiantsCrees().isEmpty()) {
                List<String[]> ids = new ArrayList<>();
                ids.add(new String[]{String.join(", ", r.getIdentifiantsCrees())});
                sections.add(new GenerateurFichiers.Section("Identifiants créés", ids));
            }
        }
        byte[] pdf = generateur.document("Rapport d'import", s.getNomFichier(), sections,
                "Généré le " + LocalDateTime.now().format(f) + " par SNJPro.");
        return new Fichier(pdf, "rapport_import_" + s.getId() + ".pdf", "application/pdf");
    }

    @Override
    public void supprimer(Long id) {
        ImportSession s = session(id);
        stockage.supprimer(s.getChemin());
        if (s.getStatut() == StatutImport.TERMINE) {
            // on garde la trace d'un import réalisé : seul le fichier est supprimé
            s.setStatut(StatutImport.ANNULE);
            sessionRepository.save(s);
        } else {
            sessionRepository.delete(s);
        }
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, s.getNomFichier());
    }

    // =====================================================================
    // Outils
    // =====================================================================

    private List<LigneAnalysee> analyserLignes(ImportSession s) {
        LecteurFichierTabulaire.Tableau tableau = lire(s);
        Map<String, String> corr = correspondance(s);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < tableau.entetes().size(); i++) {
            String champ = corr.get(tableau.entetes().get(i));
            if (champ != null) index.put(champ, i);
        }
        List<LigneAnalysee> resultat = new ArrayList<>();
        Set<String> cles = new HashSet<>();
        for (LecteurFichierTabulaire.Ligne ligne : tableau.lignes()) {
            Map<String, String> v = new HashMap<>();
            index.forEach((cle, i) -> v.put(cle, i < ligne.valeurs().size() ? ligne.valeurs().get(i).trim() : ""));
            if (v.values().stream().allMatch(String::isEmpty)) continue;
            List<ImportErreurDTO> erreurs = new ArrayList<>();
            RechercheImportServiceImpl.LigneMembre lm = importeur.valider(ligne.numero(), v, erreurs);
            List<ErreurLigne> erreursLigne = erreurs.stream().map(e -> ErreurLigne.builder().ligne(e.getNumeroLigne())
                    .champ(e.getChamp()).valeur(e.getValeur()).message(e.getMessage()).suggestion(suggestion(e)).build()).toList();
            List<String> motifsFichier = List.of();
            Membre existant = null;
            List<String> motifsExistant = new ArrayList<>();
            if (erreursLigne.isEmpty()) {
                motifsFichier = importeur.doublonsDansFichier(lm, cles);
                existant = chercherExistant(lm, motifsExistant);
            }
            resultat.add(new LigneAnalysee(ligne.numero(), lm, erreursLigne, motifsFichier, existant, motifsExistant));
        }
        return resultat;
    }

    private Membre chercherExistant(RechercheImportServiceImpl.LigneMembre lm, List<String> motifs) {
        Membre trouve = null;
        if (lm.telephone != null) {
            Optional<Membre> m = membreRepository.findFirstByNumeroTelephone(lm.telephone);
            if (m.isPresent()) { trouve = m.get(); motifs.add("Même numéro de téléphone"); }
        }
        if (lm.email != null) {
            Optional<Membre> m = membreRepository.findFirstByEmailIgnoreCase(lm.email);
            if (m.isPresent() && (trouve == null || trouve.getId().equals(m.get().getId()))) {
                trouve = m.get();
                motifs.add("Même adresse email");
            }
        }
        if (lm.dateNaissance != null) {
            Optional<Membre> m = membreRepository.findHomonymes(lm.nom, lm.prenom, lm.dateNaissance).stream().findFirst();
            if (m.isPresent() && (trouve == null || trouve.getId().equals(m.get().getId()))) {
                trouve = m.get();
                motifs.add("Même nom, prénom et date de naissance");
            }
        }
        if (trouve != null && trouve.getStatut() == StatutMembreEnum.ARCHIVE) motifs.add("La fiche existante est archivée");
        return trouve;
    }

    /** Fusion : complète uniquement les champs vides de la fiche existante (aucune donnée écrasée). */
    private void fusionner(Membre m, RechercheImportServiceImpl.LigneMembre lm, String auteur) {
        List<String> completes = new ArrayList<>();
        if (m.getDateNaissance() == null && lm.dateNaissance != null) { m.setDateNaissance(lm.dateNaissance); completes.add("dateNaissance"); }
        if (!StringUtils.hasText(m.getLieuNaissance()) && lm.lieuNaissance != null) { m.setLieuNaissance(lm.lieuNaissance); completes.add("lieuNaissance"); }
        if (!StringUtils.hasText(m.getNumeroTelephone()) && lm.telephone != null) {
            m.setNumeroTelephone(lm.telephone);
            completes.add("telephone");
        } else if (lm.telephone != null && !lm.telephone.equals(DoublonDetecteur.normaliserTelephone(m.getNumeroTelephone()))
                && !StringUtils.hasText(m.getTelephoneSecondaire())) {
            m.setTelephoneSecondaire(lm.telephone);
            completes.add("telephoneSecondaire");
        }
        if (!StringUtils.hasText(m.getEmail()) && lm.email != null) { m.setEmail(lm.email); completes.add("email"); }
        if (lm.region != null || lm.cercle != null || lm.commune != null || lm.quartier != null) {
            Localisation loc = m.getLocalisation();
            if (loc == null) {
                loc = Localisation.builder().membre(m).pays("Mali").build();
                m.setLocalisation(loc);
            }
            if (!StringUtils.hasText(loc.getRegion()) && lm.region != null) { loc.setRegion(lm.region); completes.add("region"); }
            if (!StringUtils.hasText(loc.getCercle()) && lm.cercle != null) { loc.setCercle(lm.cercle); completes.add("cercle"); }
            if (!StringUtils.hasText(loc.getCommune()) && lm.commune != null) { loc.setCommune(lm.commune); completes.add("commune"); }
            if (!StringUtils.hasText(loc.getQuartier()) && lm.quartier != null) { loc.setQuartier(lm.quartier); completes.add("quartier"); }
        }
        if (lm.profession != null || lm.statutProfessionnel != null || lm.secteurActivite != null || lm.employeur != null) {
            SituationProfessionnelle sp = m.getSituationProfessionnelle();
            if (sp == null) {
                sp = SituationProfessionnelle.builder().membre(m).build();
                m.setSituationProfessionnelle(sp);
            }
            if (lm.statutProfessionnel != null && (sp.getStatut() == null || sp.getStatut() == StatutProfessionnelEnum.AUTRE)) {
                sp.setStatut(lm.statutProfessionnel);
                completes.add("statutProfessionnel");
            }
            if (!StringUtils.hasText(sp.getProfession()) && lm.profession != null) { sp.setProfession(lm.profession); completes.add("profession"); }
            if (!StringUtils.hasText(sp.getSecteurActivite()) && lm.secteurActivite != null) { sp.setSecteurActivite(lm.secteurActivite); completes.add("secteurActivite"); }
            if (!StringUtils.hasText(sp.getNomEmployeur()) && lm.employeur != null) { sp.setNomEmployeur(lm.employeur); completes.add("employeur"); }
        }
        Set<String> existantes = new HashSet<>();
        m.getCompetences().forEach(c -> existantes.add(DoublonDetecteur.normaliserTexte(c.getIntitule())));
        for (String c : lm.competences) {
            if (existantes.add(DoublonDetecteur.normaliserTexte(c))) {
                m.getCompetences().add(MembreCompetence.builder().membre(m).intitule(c).build());
                completes.add("competence:" + c);
            }
        }
        membreRepository.save(m);
        historiqueService.enregistrer("FUSION_IMPORT", "MEMBRE", m.getId(), "Complété par import (" + auteur + ") : "
                + (completes.isEmpty() ? "aucun champ vide à compléter" : String.join(", ", completes)));
    }

    private Verification verification(ImportSession s, List<LigneAnalysee> lignes) {
        Map<String, DecisionDoublon> decisions = decisions(s);
        List<ErreurLigne> erreurs = new ArrayList<>();
        List<DoublonLigne> doublons = new ArrayList<>();
        int valides = 0, enErreur = 0;
        for (LigneAnalysee l : lignes) {
            if (!l.valide()) { enErreur++; erreurs.addAll(l.erreurs()); continue; }
            valides++;
            if (l.doublon()) doublons.add(doublon(l, decisions.get(String.valueOf(l.numero()))));
        }
        return Verification.builder()
                .session(toSession(s))
                .totalLignes(lignes.size())
                .lignesValides(valides)
                .lignesEnErreur(enErreur)
                .doublonsDetectes(doublons.size())
                .doublonsSansDecision((int) doublons.stream().filter(d -> d.getDecision() == null).count())
                .erreurs(erreurs)
                .doublons(doublons)
                .build();
    }

    private DoublonLigne doublon(LigneAnalysee l, DecisionDoublon decision) {
        RechercheImportServiceImpl.LigneMembre lm = l.membre();
        Membre m = l.existant();
        List<Comparaison> comparaison = new ArrayList<>();
        int score = 100; // doublon interne au fichier
        if (m != null) {
            comparer(comparaison, "Prénom", lm.prenom, m.getPrenom());
            comparer(comparaison, "Nom", lm.nom, m.getNom());
            comparer(comparaison, "Sexe", str(lm.sexe), str(m.getSexe()));
            comparer(comparaison, "Date de naissance", str(lm.dateNaissance), str(m.getDateNaissance()));
            comparer(comparaison, "Lieu de naissance", lm.lieuNaissance, m.getLieuNaissance());
            comparer(comparaison, "Téléphone", lm.telephone, DoublonDetecteur.normaliserTelephone(m.getNumeroTelephone()));
            comparer(comparaison, "Email", lm.email, m.getEmail());
            comparer(comparaison, "Région", lm.region, m.getLocalisation() == null ? null : m.getLocalisation().getRegion());
            comparer(comparaison, "Profession", lm.profession,
                    m.getSituationProfessionnelle() == null ? null : m.getSituationProfessionnelle().getProfession());
            long renseignes = comparaison.stream().filter(c -> c.getValeurFichier() != null && c.getValeurExistante() != null).count();
            long identiques = comparaison.stream().filter(Comparaison::isIdentique).count();
            score = renseignes == 0 ? 50 : (int) Math.round(100.0 * identiques / renseignes);
            if (l.motifsExistant().contains("Même numéro de téléphone") || l.motifsExistant().contains("Même adresse email"))
                score = Math.max(score, 80);
        }
        return DoublonLigne.builder()
                .ligne(l.numero())
                .nomFichier(lm.prenom + " " + lm.nom)
                .membreExistantId(m == null ? null : m.getId())
                .identifiantSnj(m == null ? null : m.getIdentifiantSnj())
                .nomExistant(m == null ? null : m.getPrenom() + " " + m.getNom())
                .score(score)
                .motifs(tousMotifs(l))
                .comparaison(comparaison)
                .decision(decision)
                .build();
    }

    private static List<String> tousMotifs(LigneAnalysee l) {
        List<String> motifs = new ArrayList<>(l.motifsExistant());
        motifs.addAll(l.motifsFichier());
        return motifs;
    }

    private static void comparer(List<Comparaison> liste, String champ, String fichier, String existant) {
        String a = StringUtils.hasText(fichier) ? fichier.trim() : null;
        String b = StringUtils.hasText(existant) ? existant.trim() : null;
        if (a == null && b == null) return;
        boolean identique = a != null && b != null && DoublonDetecteur.normaliserTexte(a).equals(DoublonDetecteur.normaliserTexte(b));
        liste.add(Comparaison.builder().champ(champ).valeurFichier(a).valeurExistante(b).identique(identique).build());
    }

    /** Proposition de correction affichée à côté de l'erreur (étape « Vérification »). */
    private static String suggestion(ImportErreurDTO e) {
        String champ = e.getChamp() == null ? "" : e.getChamp();
        String valeur = e.getValeur() == null ? "" : e.getValeur().trim();
        String chiffres = valeur.replaceAll("[^0-9]", "");
        return switch (champ) {
            case "prenom", "nom" -> valeur.isEmpty() ? "Renseignez la valeur dans le fichier" : "Raccourcissez la valeur à 50 caractères";
            case "sexe" -> "Utilisez M ou F";
            case "dateNaissance" -> e.getMessage() != null && e.getMessage().contains(" ans")
                    ? "Vérifiez la date : le membre doit être né avant le " + LocalDate.now().minusYears(MembreServiceImpl.AGE_MINIMUM).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : chiffres.length() == 8
                    ? "Essayez " + chiffres.substring(0, 2) + "/" + chiffres.substring(2, 4) + "/" + chiffres.substring(4)
                    : "Format attendu : JJ/MM/AAAA (ex : 15/03/1998)";
            case "telephone" -> chiffres.length() == 8 ? "Essayez +223" + chiffres : "Format attendu : +223 suivi de 8 chiffres";
            case "email" -> valeur.contains("@") && !valeur.substring(valeur.indexOf('@')).contains(".")
                    ? "Domaine incomplet : ex. " + valeur + ".com" : "Format attendu : nom@domaine.ml";
            case "statutProfessionnel" -> "Valeurs acceptées : " + Arrays.toString(StatutProfessionnelEnum.values());
            default -> null;
        };
    }

    /** Correspondance approximative (état « À vérifier ») : l'en-tête contient un synonyme connu. */
    private String colonneProbable(String entete) {
        String n = DoublonDetecteur.normaliserTexte(entete);
        if (n.length() < 3) return null;
        for (Map.Entry<String, List<String>> e : RechercheImportServiceImpl.COLONNES.entrySet()) {
            for (String syn : e.getValue()) {
                if (syn.length() >= 3 && (n.contains(syn) || syn.contains(n))) return e.getKey();
            }
        }
        return null;
    }

    private Analyse analyse(ImportSession s, LecteurFichierTabulaire.Tableau tableau) {
        Map<String, String> corr = correspondance(s);
        LecteurFichierTabulaire.Ligne premiere = tableau.lignes().stream()
                .filter(l -> l.valeurs().stream().anyMatch(StringUtils::hasText)).findFirst().orElse(null);
        List<Colonne> colonnes = new ArrayList<>();
        Map<String, String> apercu = new LinkedHashMap<>();
        for (int i = 0; i < tableau.entetes().size(); i++) {
            String entete = tableau.entetes().get(i);
            if (!StringUtils.hasText(entete)) continue;
            final int col = i;
            String exemple = tableau.lignes().stream()
                    .map(l -> col < l.valeurs().size() ? l.valeurs().get(col) : "")
                    .filter(StringUtils::hasText).findFirst().orElse("");
            String champ = corr.get(entete);
            EtatColonne etat = champ == null ? EtatColonne.IGNOREE
                    : champ.equals(importeur.colonnePour(entete)) ? EtatColonne.AUTO : EtatColonne.A_VERIFIER;
            colonnes.add(Colonne.builder().entete(entete).exemple(exemple).champ(champ).etat(etat).build());
            if (champ != null && premiere != null)
                apercu.put(champ, col < premiere.valeurs().size() ? premiere.valeurs().get(col) : "");
        }
        List<Champ> champs = LIBELLES.entrySet().stream().map(e -> Champ.builder().cle(e.getKey()).libelle(e.getValue())
                .obligatoire(RechercheImportServiceImpl.COLONNES_OBLIGATOIRES.contains(e.getKey())).build()).toList();
        return Analyse.builder().session(toSession(s)).colonnes(colonnes).champsDisponibles(champs)
                .champsObligatoiresManquants(manquants(corr)).apercu(apercu).build();
    }

    private void exigerObligatoires(ImportSession s) {
        List<String> manquants = manquants(correspondance(s));
        if (!manquants.isEmpty())
            throw new IllegalArgumentException("Associez d'abord les champs obligatoires : " + String.join(", ", manquants));
    }

    private static List<String> manquants(Map<String, String> corr) {
        Collection<String> associes = corr.values();
        return RechercheImportServiceImpl.COLONNES_OBLIGATOIRES.stream()
                .filter(c -> !associes.contains(c)).map(ImportAssistantServiceImpl::libelle).toList();
    }

    private Session toSession(ImportSession s) {
        String cohorteNom = cohorteRepository.findById(s.getCohorteId()).map(Cohorte::getNom).orElse(null);
        return Session.builder().id(s.getId()).nomFichier(s.getNomFichier()).taille(s.getTaille())
                .cohorteId(s.getCohorteId()).cohorteNom(cohorteNom).statut(s.getStatut())
                .totalLignes(s.getTotalLignes()).importes(s.getImportes()).fusionnes(s.getFusionnes())
                .ignores(s.getIgnores()).erreurs(s.getErreurs()).doublons(s.getDoublons())
                .creePar(s.getCreePar()).dateCreation(s.getDateCreation()).dateImport(s.getDateImport()).build();
    }

    private LecteurFichierTabulaire.Tableau lire(ImportSession s) {
        if (s.getStatut() == StatutImport.ANNULE)
            throw new ConflitException("Le fichier de cet import a été supprimé.");
        try {
            return lecteur.lire(stockage.lire(s.getChemin()), s.getNomFichier());
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible : " + e.getMessage());
        }
    }

    private ImportSession session(Long id) {
        ImportSession s = sessionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Import non trouvé : " + id));
        perimetre.exigerCohorte(s.getCohorteId());
        return s;
    }

    /** Une fusion modifie la fiche existante : interdite si cette fiche est hors du périmètre de l'utilisateur. */
    private boolean peutFusionner(Membre existant) {
        return existant != null && perimetre.couvre(existant);
    }

    private ImportSession modifiable(Long id) {
        ImportSession s = session(id);
        if (s.getStatut() == StatutImport.TERMINE || s.getStatut() == StatutImport.ANNULE)
            throw new ConflitException("Cet import est déjà terminé.");
        return s;
    }

    private Cohorte cohorte(Long id) {
        if (id == null) throw new IllegalArgumentException("La cohorte est obligatoire.");
        return cohorteRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + id));
    }

    private Map<String, String> correspondance(ImportSession s) {
        return s.getCorrespondance() == null ? new LinkedHashMap<>()
                : lireJson(s.getCorrespondance(), new TypeReference<LinkedHashMap<String, String>>() { });
    }

    private Map<String, DecisionDoublon> decisions(ImportSession s) {
        return s.getDecisions() == null ? new HashMap<>()
                : lireJson(s.getDecisions(), new TypeReference<HashMap<String, DecisionDoublon>>() { });
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T lireJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String libelle(String champ) { return champ == null ? "" : LIBELLES.getOrDefault(champ, champ); }
    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static String nvl(String s) { return s == null ? "" : s; }
}
