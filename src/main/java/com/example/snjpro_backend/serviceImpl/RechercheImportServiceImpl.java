package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.DoublonGroupeDTO;
import com.example.snjpro_backend.dto.ImportResultDTO;
import com.example.snjpro_backend.dto.ImportResultDTO.ImportErreurDTO;
import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.dto.MembreResponseDTO;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.mapper.MembreMapper;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import com.example.snjpro_backend.serviceInterface.RechercheImportServiceInterface;
import com.example.snjpro_backend.specification.MembreSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RechercheImportServiceImpl implements RechercheImportServiceInterface {

    /** Colonnes reconnues : clé interne -> en-têtes acceptés (normalisés : minuscules, sans accents). */
    static final Map<String, List<String>> COLONNES = new LinkedHashMap<>();
    static {
        COLONNES.put("prenom", List.of("prenom", "prenoms", "first name"));
        COLONNES.put("nom", List.of("nom", "nom de famille", "last name"));
        COLONNES.put("sexe", List.of("sexe", "genre"));
        COLONNES.put("dateNaissance", List.of("date naissance", "date de naissance", "datenaissance", "ne le", "naissance"));
        COLONNES.put("lieuNaissance", List.of("lieu naissance", "lieu de naissance", "lieunaissance", "ne a"));
        COLONNES.put("telephone", List.of("telephone", "tel", "numero telephone", "numero de telephone", "contact", "numerotelephone"));
        COLONNES.put("email", List.of("email", "e mail", "mail", "courriel"));
        COLONNES.put("region", List.of("region"));
        COLONNES.put("cercle", List.of("cercle"));
        COLONNES.put("commune", List.of("commune"));
        COLONNES.put("quartier", List.of("quartier"));
        COLONNES.put("profession", List.of("profession", "metier", "fonction"));
        COLONNES.put("statutProfessionnel", List.of("statut professionnel", "situation", "situation professionnelle", "statutprofessionnel"));
        COLONNES.put("secteurActivite", List.of("secteur", "secteur activite", "secteur d activite", "secteuractivite"));
        COLONNES.put("employeur", List.of("employeur", "nom employeur", "entreprise"));
        COLONNES.put("competences", List.of("competences", "competence"));
    }
    static final List<String> COLONNES_OBLIGATOIRES = List.of("prenom", "nom", "sexe");
    private static final List<DateTimeFormatter> FORMATS_DATE = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"));
    private static final Pattern TELEPHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final int MAX_LIGNES = 5000;

    private final MembreRepository membreRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreServiceInterface membreService;
    private final LecteurFichierTabulaire lecteur;
    private final DoublonDetecteur doublonDetecteur;
    private final MembreMapper mapper;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Recherche avancée (F13 / F14)
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public Page<MembreResponseDTO> rechercheAvancee(MembreFilterDTO filter, Pageable pageable) {
        perimetre.restreindre(filter);
        return membreRepository.findAll(MembreSpecification.avecFiltres(filter), pageable).map(mapper::toResponseDTO);
    }

    // =====================================================================
    // Import Excel / CSV (F15) + validation des données
    // =====================================================================

    @Override
    public ImportResultDTO importerFichier(MultipartFile fichier, Long cohorteId, String createdBy, boolean simulation) {
        if (fichier == null || fichier.isEmpty())
            throw new IllegalArgumentException("Aucun fichier fourni ou fichier vide.");
        Cohorte cohorte = cohorteRepository.findById(cohorteId)
                .orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + cohorteId));
        if (cohorte.getStatut() == StatutCohorteEnum.ARCHIVEE)
            throw new ConflitException("Impossible d'importer dans une cohorte archivée.");

        LecteurFichierTabulaire.Tableau tableau;
        try {
            tableau = lecteur.lire(fichier);
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible : " + e.getMessage());
        }

        ImportResultDTO resultat = ImportResultDTO.builder().simulation(simulation).build();

        // 1. Vérification des colonnes
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < tableau.entetes().size(); i++) {
            String entete = tableau.entetes().get(i);
            String cle = colonnePour(entete);
            if (cle != null && !index.containsKey(cle)) {
                index.put(cle, i);
                resultat.getColonnesReconnues().add(cle);
            } else if (StringUtils.hasText(entete)) {
                resultat.getColonnesIgnorees().add(entete);
            }
        }
        List<String> manquantes = COLONNES_OBLIGATOIRES.stream().filter(c -> !index.containsKey(c)).toList();
        if (!manquantes.isEmpty()) {
            resultat.setMessage("Colonnes obligatoires manquantes : " + String.join(", ", manquantes)
                    + ". Téléchargez le modèle via GET /api/import/modele.");
            return resultat;
        }
        if (tableau.lignes().size() > MAX_LIGNES)
            throw new IllegalArgumentException("Le fichier dépasse " + MAX_LIGNES + " lignes : découpez-le en plusieurs fichiers.");

        // 2. Contrôle et import ligne par ligne
        Set<String> clesFichier = new HashSet<>();
        for (LecteurFichierTabulaire.Ligne ligne : tableau.lignes()) {
            Map<String, String> v = new HashMap<>();
            index.forEach((cle, i) -> v.put(cle, i < ligne.valeurs().size() ? ligne.valeurs().get(i).trim() : ""));
            if (v.values().stream().allMatch(String::isEmpty)) continue; // ligne vide
            resultat.setTotalLignes(resultat.getTotalLignes() + 1);

            List<ImportErreurDTO> erreurs = new ArrayList<>();
            LigneMembre lm = valider(ligne.numero(), v, erreurs);
            if (!erreurs.isEmpty()) {
                resultat.getErreurs().addAll(erreurs);
                resultat.setLignesIgnorees(resultat.getLignesIgnorees() + 1);
                continue;
            }

            List<String> motifs = new ArrayList<>(doublonDetecteur.motifs(lm.prenom, lm.nom, lm.dateNaissance,
                    lm.telephone, lm.email, null));
            motifs.addAll(doublonsDansFichier(lm, clesFichier));
            if (!motifs.isEmpty()) {
                resultat.setDoublonsDetectes(resultat.getDoublonsDetectes() + 1);
                resultat.setLignesIgnorees(resultat.getLignesIgnorees() + 1);
                resultat.getErreurs().add(erreur(ligne.numero(), "doublon", lm.prenom + " " + lm.nom, String.join(" ; ", motifs)));
                continue;
            }

            if (!simulation) {
                resultat.getIdentifiantsCrees().add(enregistrer(lm, cohorte, createdBy));
            }
            resultat.setImportesAvecSucces(resultat.getImportesAvecSucces() + 1);
        }

        resultat.setMessage(String.format("%s : %d ligne(s) traitée(s), %d %s, %d ignorée(s) dont %d doublon(s).",
                simulation ? "Simulation terminée" : "Import terminé",
                resultat.getTotalLignes(), resultat.getImportesAvecSucces(),
                simulation ? "valide(s)" : "importée(s)",
                resultat.getLignesIgnorees(), resultat.getDoublonsDetectes()));
        if (!simulation) {
            historiqueService.enregistrer("IMPORT", "COHORTE", cohorteId,
                    fichier.getOriginalFilename() + " - " + resultat.getMessage());
        }
        return resultat;
    }

    @Override @Transactional(readOnly = true)
    public byte[] modeleImportCsv() {
        String contenu = "﻿"
                + "prenom;nom;sexe;date_naissance;lieu_naissance;telephone;email;region;cercle;commune;quartier;"
                + "profession;statut_professionnel;secteur_activite;employeur;competences\n"
                + "Awa;TRAORE;F;15/03/1998;Ségou;+22370000001;awa.traore@example.com;Bamako;;Commune IV;Lafiabougou;"
                + "Infirmière;EMPLOYE;Santé;CHU Gabriel Touré;Secourisme, Informatique\n"
                + "Moussa;KONE;M;1996-07-02;Sikasso;76000002;;Sikasso;Sikasso;;;"
                + "Maçon;AUTO_EMPLOYE;BTP;;Maçonnerie\n";
        return contenu.getBytes(StandardCharsets.UTF_8);
    }

    // =====================================================================
    // Détection et fusion des doublons (F16)
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<DoublonGroupeDTO> detecterDoublons() {
        List<Membre> membres = membreRepository.findAllNonArchives();
        // clé -> (critère, score, membres)
        Map<String, List<Membre>> groupes = new LinkedHashMap<>();
        Map<String, String> criteres = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();
        for (Membre m : membres) {
            String nom = DoublonDetecteur.normaliserTexte(m.getNom());
            String prenom = DoublonDetecteur.normaliserTexte(m.getPrenom());
            // nom et prénom triés : détecte aussi les inversions nom/prénom à la saisie
            String nomsTries = nom.compareTo(prenom) <= 0 ? nom + "|" + prenom : prenom + "|" + nom;
            ajouter(groupes, criteres, scores, "TEL:" + DoublonDetecteur.normaliserTelephone(m.getNumeroTelephone()),
                    m.getNumeroTelephone() != null, m, "Même numéro de téléphone", 0.95);
            ajouter(groupes, criteres, scores, "EMAIL:" + (m.getEmail() == null ? "" : m.getEmail().toLowerCase()),
                    StringUtils.hasText(m.getEmail()), m, "Même adresse email", 0.9);
            ajouter(groupes, criteres, scores, "IDENT:" + nomsTries + "|" + m.getDateNaissance(),
                    m.getDateNaissance() != null, m, "Même nom, prénom et date de naissance", 0.9);
            ajouter(groupes, criteres, scores, "NOMS:" + nomsTries,
                    !nom.isEmpty() && !prenom.isEmpty(), m, "Même nom et prénom", 0.6);
        }

        // Un même ensemble de membres peut ressortir sur plusieurs critères : on garde le score le plus élevé
        Map<Set<Long>, DoublonGroupeDTO> resultat = new LinkedHashMap<>();
        groupes.forEach((cle, liste) -> {
            if (liste.size() < 2) return;
            Set<Long> ids = new TreeSet<>();
            liste.forEach(m -> ids.add(m.getId()));
            DoublonGroupeDTO existant = resultat.get(ids);
            if (existant == null || existant.getScoreSimilarite() < scores.get(cle)) {
                resultat.put(ids, DoublonGroupeDTO.builder()
                        .criteresDetection(criteres.get(cle))
                        .scoreSimilarite(scores.get(cle))
                        .membres(liste.stream().map(mapper::toResponseDTO).toList())
                        .build());
            }
        });
        return resultat.values().stream()
                .sorted(Comparator.comparingDouble(DoublonGroupeDTO::getScoreSimilarite).reversed())
                .toList();
    }

    @Override
    public MembreResponseDTO fusionnerMembres(Long membreIdPrincipal, Long membreIdDoublon) {
        if (Objects.equals(membreIdPrincipal, membreIdDoublon))
            throw new IllegalArgumentException("Le membre principal et le doublon doivent être différents.");
        Membre principal = membreRepository.findById(membreIdPrincipal)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + membreIdPrincipal));
        Membre doublon = membreRepository.findById(membreIdDoublon)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + membreIdDoublon));
        if (doublon.getStatut() == StatutMembreEnum.ARCHIVE)
            throw new ConflitException("Le membre " + doublon.getIdentifiantSnj() + " est déjà archivé.");

        // Coordonnées : on complète uniquement ce qui manque sur la fiche principale
        String telDoublon = doublon.getNumeroTelephone();
        String emailDoublon = doublon.getEmail();
        doublon.setNumeroTelephone(null);
        doublon.setEmail(null);
        membreRepository.saveAndFlush(doublon); // libère téléphone / email avant de les reporter
        if (principal.getDateNaissance() == null) principal.setDateNaissance(doublon.getDateNaissance());
        if (!StringUtils.hasText(principal.getLieuNaissance())) principal.setLieuNaissance(doublon.getLieuNaissance());
        if (!StringUtils.hasText(principal.getNumeroTelephone())) principal.setNumeroTelephone(telDoublon);
        if (!StringUtils.hasText(principal.getEmail())) principal.setEmail(emailDoublon);
        if (!StringUtils.hasText(principal.getPhotoUrl())) principal.setPhotoUrl(doublon.getPhotoUrl());
        if (principal.getUserId() == null && doublon.getUserId() != null) {
            principal.setUserId(doublon.getUserId());
            doublon.setUserId(null);
        }

        SituationProfessionnelle spD = doublon.getSituationProfessionnelle();
        if (principal.getSituationProfessionnelle() == null && spD != null) {
            principal.setSituationProfessionnelle(SituationProfessionnelle.builder()
                    .membre(principal).statut(spD.getStatut()).profession(spD.getProfession())
                    .secteurActivite(spD.getSecteurActivite()).nomEmployeur(spD.getNomEmployeur())
                    .description(spD.getDescription()).dateDebutActivite(spD.getDateDebutActivite())
                    .build());
        }
        Localisation locD = doublon.getLocalisation();
        if (principal.getLocalisation() == null && locD != null) {
            principal.setLocalisation(Localisation.builder()
                    .membre(principal).pays(locD.getPays()).region(locD.getRegion()).cercle(locD.getCercle())
                    .commune(locD.getCommune()).quartier(locD.getQuartier()).adresseComplete(locD.getAdresseComplete())
                    .build());
        }
        Set<String> competencesExistantes = new HashSet<>();
        principal.getCompetences().forEach(c -> competencesExistantes.add(c.getIntitule().toLowerCase()));
        for (MembreCompetence c : doublon.getCompetences()) {
            if (competencesExistantes.add(c.getIntitule().toLowerCase())) {
                principal.getCompetences().add(MembreCompetence.builder()
                        .membre(principal).intitule(c.getIntitule()).categorie(c.getCategorie())
                        .niveau(c.getNiveau()).description(c.getDescription()).build());
            }
        }
        Set<String> parcoursExistants = new HashSet<>();
        principal.getParcours().forEach(p -> parcoursExistants.add(p.getIntitule().toLowerCase() + "|" + p.getDateDebut()));
        for (ParcoursSNJ p : doublon.getParcours()) {
            if (parcoursExistants.add(p.getIntitule().toLowerCase() + "|" + p.getDateDebut())) {
                principal.getParcours().add(ParcoursSNJ.builder()
                        .membre(principal).intitule(p.getIntitule()).type(p.getType())
                        .description(p.getDescription()).dateDebut(p.getDateDebut())
                        .dateFin(p.getDateFin()).lieu(p.getLieu()).build());
            }
        }

        doublon.setStatut(StatutMembreEnum.ARCHIVE);
        membreRepository.save(doublon);
        Membre resultat = membreRepository.save(principal);
        historiqueService.enregistrer("FUSION", "MEMBRE", principal.getId(),
                doublon.getIdentifiantSnj() + " fusionné dans " + principal.getIdentifiantSnj() + " (doublon archivé)");
        return mapper.toResponseDTO(resultat);
    }

    // =====================================================================
    // Utilitaires d'import
    // =====================================================================

    /** Données d'une ligne après validation. */
    public static class LigneMembre {
        String prenom, nom, lieuNaissance, telephone, email;
        String region, cercle, commune, quartier;
        String profession, secteurActivite, employeur;
        SexeEnum sexe;
        LocalDate dateNaissance;
        StatutProfessionnelEnum statutProfessionnel;
        List<String> competences = new ArrayList<>();
    }

    public LigneMembre valider(int numero, Map<String, String> v, List<ImportErreurDTO> erreurs) {
        LigneMembre lm = new LigneMembre();
        lm.prenom = v.getOrDefault("prenom", "");
        lm.nom = v.getOrDefault("nom", "");
        if (lm.prenom.isEmpty()) erreurs.add(erreur(numero, "prenom", "", "Le prénom est obligatoire"));
        if (lm.nom.isEmpty()) erreurs.add(erreur(numero, "nom", "", "Le nom est obligatoire"));
        if (lm.prenom.length() > 50) erreurs.add(erreur(numero, "prenom", lm.prenom, "50 caractères maximum"));
        if (lm.nom.length() > 50) erreurs.add(erreur(numero, "nom", lm.nom, "50 caractères maximum"));

        String sexe = v.getOrDefault("sexe", "");
        lm.sexe = parserSexe(sexe);
        if (lm.sexe == null)
            erreurs.add(erreur(numero, "sexe", sexe, "Valeur attendue : M/H/MASCULIN ou F/FEMININ"));

        String date = v.getOrDefault("dateNaissance", "");
        if (!date.isEmpty()) {
            lm.dateNaissance = parserDate(date);
            if (lm.dateNaissance == null)
                erreurs.add(erreur(numero, "dateNaissance", date, "Date invalide (formats acceptés : JJ/MM/AAAA ou AAAA-MM-JJ)"));
            else if (!lm.dateNaissance.isBefore(LocalDate.now()) || lm.dateNaissance.getYear() < 1900)
                erreurs.add(erreur(numero, "dateNaissance", date, "Date de naissance incohérente"));
        }

        String tel = v.getOrDefault("telephone", "");
        if (!tel.isEmpty()) {
            lm.telephone = DoublonDetecteur.normaliserTelephone(tel);
            if (lm.telephone == null || !TELEPHONE.matcher(lm.telephone).matches())
                erreurs.add(erreur(numero, "telephone", tel, "Numéro de téléphone invalide"));
        }
        String email = v.getOrDefault("email", "");
        if (!email.isEmpty()) {
            lm.email = email.toLowerCase();
            if (!EMAIL.matcher(lm.email).matches())
                erreurs.add(erreur(numero, "email", email, "Adresse email invalide"));
        }

        String statut = v.getOrDefault("statutProfessionnel", "");
        if (!statut.isEmpty()) {
            lm.statutProfessionnel = parserStatutProfessionnel(statut);
            if (lm.statutProfessionnel == null)
                erreurs.add(erreur(numero, "statutProfessionnel", statut,
                        "Valeur attendue : " + Arrays.toString(StatutProfessionnelEnum.values())));
        }

        lm.lieuNaissance = vide(v.get("lieuNaissance"));
        lm.region = vide(v.get("region"));
        lm.cercle = vide(v.get("cercle"));
        lm.commune = vide(v.get("commune"));
        lm.quartier = vide(v.get("quartier"));
        lm.profession = vide(v.get("profession"));
        lm.secteurActivite = vide(v.get("secteurActivite"));
        lm.employeur = vide(v.get("employeur"));
        String competences = v.getOrDefault("competences", "");
        if (!competences.isEmpty()) {
            Set<String> uniques = new LinkedHashSet<>();
            for (String c : competences.split("[;,|]")) {
                if (StringUtils.hasText(c) && c.trim().length() <= 100) uniques.add(c.trim());
            }
            lm.competences.addAll(uniques);
        }
        return lm;
    }

    /** Détecte les doublons à l'intérieur du fichier lui-même. */
    public List<String> doublonsDansFichier(LigneMembre lm, Set<String> cles) {
        List<String> motifs = new ArrayList<>();
        if (lm.telephone != null && !cles.add("TEL:" + lm.telephone))
            motifs.add("Téléphone présent plusieurs fois dans le fichier");
        if (lm.email != null && !cles.add("EMAIL:" + lm.email))
            motifs.add("Email présent plusieurs fois dans le fichier");
        if (lm.dateNaissance != null && !cles.add("IDENT:" + DoublonDetecteur.normaliserTexte(lm.nom) + "|"
                + DoublonDetecteur.normaliserTexte(lm.prenom) + "|" + lm.dateNaissance))
            motifs.add("Même nom, prénom et date de naissance présents plusieurs fois dans le fichier");
        return motifs;
    }

    public String enregistrer(LigneMembre lm, Cohorte cohorte, String createdBy) {
        Membre membre = Membre.builder()
                .identifiantSnj(membreService.genererIdentifiantSnj(cohorte.getId()))
                .prenom(lm.prenom)
                .nom(lm.nom)
                .sexe(lm.sexe)
                .dateNaissance(lm.dateNaissance)
                .lieuNaissance(lm.lieuNaissance)
                .numeroTelephone(lm.telephone)
                .email(lm.email)
                .statut(StatutMembreEnum.ACTIF)
                .cohorte(cohorte)
                .createdBy(createdBy)
                .build();
        if (lm.region != null || lm.cercle != null || lm.commune != null || lm.quartier != null) {
            membre.setLocalisation(Localisation.builder()
                    .membre(membre).pays("Mali").region(lm.region).cercle(lm.cercle)
                    .commune(lm.commune).quartier(lm.quartier).build());
        }
        if (lm.profession != null || lm.statutProfessionnel != null || lm.secteurActivite != null || lm.employeur != null) {
            membre.setSituationProfessionnelle(SituationProfessionnelle.builder()
                    .membre(membre)
                    .statut(lm.statutProfessionnel != null ? lm.statutProfessionnel : StatutProfessionnelEnum.AUTRE)
                    .profession(lm.profession).secteurActivite(lm.secteurActivite).nomEmployeur(lm.employeur)
                    .build());
        }
        for (String c : lm.competences) {
            membre.getCompetences().add(MembreCompetence.builder().membre(membre).intitule(c).build());
        }
        // saveAndFlush : la séquence de l'identifiant suivant tient compte de ce membre
        return membreRepository.saveAndFlush(membre).getIdentifiantSnj();
    }

    public String colonnePour(String entete) {
        String n = DoublonDetecteur.normaliserTexte(entete);
        if (n.isEmpty()) return null;
        for (Map.Entry<String, List<String>> e : COLONNES.entrySet()) {
            if (e.getValue().contains(n) || DoublonDetecteur.normaliserTexte(e.getKey()).equals(n)) return e.getKey();
        }
        return null;
    }

    private SexeEnum parserSexe(String s) {
        return switch (DoublonDetecteur.normaliserTexte(s)) {
            case "m", "h", "homme", "masculin", "male", "garcon" -> SexeEnum.MASCULIN;
            case "f", "femme", "feminin", "female", "fille" -> SexeEnum.FEMININ;
            default -> null;
        };
    }

    private LocalDate parserDate(String s) {
        for (DateTimeFormatter f : FORMATS_DATE) {
            try {
                return LocalDate.parse(s.trim(), f);
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return null;
    }

    private StatutProfessionnelEnum parserStatutProfessionnel(String s) {
        String n = DoublonDetecteur.normaliserTexte(s).replace(' ', '_');
        for (StatutProfessionnelEnum e : StatutProfessionnelEnum.values()) {
            if (e.name().equalsIgnoreCase(n)) return e;
        }
        return switch (n) {
            case "salarie", "employee", "fonctionnaire", "en_emploi" -> StatutProfessionnelEnum.EMPLOYE;
            case "independant", "entrepreneur", "auto_entrepreneur", "commercant" -> StatutProfessionnelEnum.AUTO_EMPLOYE;
            case "sans_emploi", "demandeur_d_emploi", "chomage" -> StatutProfessionnelEnum.CHOMEUR;
            case "eleve", "etudiante", "en_formation" -> StatutProfessionnelEnum.ETUDIANT;
            case "retraitee" -> StatutProfessionnelEnum.RETRAITE;
            default -> null;
        };
    }

    private static String vide(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static ImportErreurDTO erreur(int ligne, String champ, String valeur, String message) {
        return ImportErreurDTO.builder().numeroLigne(ligne).champ(champ).valeur(valeur).message(message).build();
    }

    private static void ajouter(Map<String, List<Membre>> groupes, Map<String, String> criteres, Map<String, Double> scores,
                                String cle, boolean applicable, Membre m, String critere, double score) {
        if (!applicable) return;
        groupes.computeIfAbsent(cle, k -> new ArrayList<>()).add(m);
        criteres.putIfAbsent(cle, critere);
        scores.putIfAbsent(cle, score);
    }
}
