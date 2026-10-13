package com.example.snjpro_backend.config;

import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.*;
import com.example.snjpro_backend.enumeration.Enumerations.*;
import com.example.snjpro_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Jeu de données de test (développement uniquement).
 * <p>
 * Chaque compte a pour mot de passe son nom d'utilisateur (ex. agent1 / agent1).
 * Les membres se connectent aussi depuis le mobile avec leur téléphone (+22370000001 → agent1).
 * Le jeu n'est inséré qu'une fois : il est ignoré si le compte « admin1 » existe déjà.
 * Désactivable avec {@code app.donnees-test.actif=false}.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.donnees-test.actif", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DonneesTest implements CommandLineRunner {
    private static final String AUTEUR = "adminsnj1";
    private static final int NB_AGENTS = 40;

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreRepository membreRepository;
    private final CompetenceReferentielRepository competenceReferentielRepository;
    private final ActiviteRepository activiteRepository;
    private final PresenceRepository presenceRepository;
    private final MobilisationRepository mobilisationRepository;
    private final ParticipationMobilisationRepository participationRepository;
    private final OpportuniteRepository opportuniteRepository;
    private final CandidatureRepository candidatureRepository;
    private final ActualiteRepository actualiteRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDestinataireRepository notificationDestinataireRepository;
    private final DemandeRepository demandeRepository;
    private final DemandeEvenementRepository demandeEvenementRepository;
    private final ConversationRepository conversationRepository;
    private final ParticipantConversationRepository participantRepository;
    private final MessageRepository messageRepository;
    private final DossierRepository dossierRepository;

    private final Random random = new Random(42);

    private static final String[] PRENOMS_H = {"Moussa", "Amadou", "Seydou", "Ibrahim", "Oumar", "Bakary",
            "Mamadou", "Souleymane", "Adama", "Boubacar", "Drissa", "Lassana", "Modibo", "Cheick", "Youssouf"};
    private static final String[] PRENOMS_F = {"Awa", "Fatoumata", "Mariam", "Aminata", "Kadiatou", "Oumou",
            "Djeneba", "Rokia", "Hawa", "Assitan", "Salimata", "Bintou"};
    private static final String[] NOMS = {"Traoré", "Coulibaly", "Diarra", "Keïta", "Koné", "Sangaré", "Diallo", "Touré",
            "Cissé", "Sidibé", "Dembélé", "Maïga", "Konaté", "Sissoko", "Doumbia", "Camara"};
    private static final String[][] REGIONS = {
            {"Bamako", "Bamako", "Commune IV", "Lafiabougou"},
            {"Bamako", "Bamako", "Commune VI", "Sogoniko"},
            {"Ségou", "Ségou", "Ségou", "Pélengana"},
            {"Sikasso", "Sikasso", "Sikasso", "Wayerma"},
            {"Kayes", "Kayes", "Kayes", "Plateau"},
            {"Koulikoro", "Kati", "Kati", "Malibougou"},
            {"Mopti", "Mopti", "Mopti", "Sévaré"},
    };
    private static final String[][] COMPETENCES = {
            {"Secourisme", "Santé"}, {"Maçonnerie", "BTP"}, {"Plomberie", "BTP"}, {"Électricité bâtiment", "BTP"},
            {"Informatique bureautique", "Numérique"}, {"Développement web", "Numérique"}, {"Conduite poids lourd", "Transport"},
            {"Agriculture maraîchère", "Agriculture"}, {"Couture", "Artisanat"}, {"Menuiserie", "BTP"},
            {"Comptabilité", "Gestion"}, {"Animation communautaire", "Social"}, {"Soins infirmiers", "Santé"},
            {"Mécanique auto", "Transport"}};
    private static final String[] DIPLOMES = {"DEF", "CAP", "BT", "BAC", "DUT / BTS", "Licence", "Master"};
    private static final String[] GROUPES = {"A+", "A-", "B+", "O+", "O-", "AB+"};

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsername("admin1")) return;
        log.info("Insertion du jeu de données de test…");

        // ------------------------------------------------------------ Cohortes
        Cohorte c1 = cohorte("Cohorte 2022 — N°23", "C2022", 23, 2022, 2023, StatutCohorteEnum.LIBEREE, "Centre d'instruction de Ségou", "Ségou");
        Cohorte c2 = cohorte("Cohorte 2023 — N°24", "C2023", 24, 2023, 2024, StatutCohorteEnum.EN_RESERVE, "Centre d'instruction de Kati", "Koulikoro");
        Cohorte c3 = cohorte("Cohorte 2024 — N°25", "C2024", 25, 2024, 2025, StatutCohorteEnum.LIBEREE, "Centre d'instruction de Sikasso", "Sikasso");
        Cohorte c4 = cohorte("Cohorte 2025 — N°26", "C2025", 26, 2025, 2026, StatutCohorteEnum.EN_FORMATION, "Centre d'instruction de Kati", "Koulikoro");
        Cohorte c0 = cohorte("Cohorte 2019 — N°20", "C2019", 20, 2019, 2020, StatutCohorteEnum.ARCHIVEE, "Centre d'instruction de Koulikoro", "Koulikoro");
        List<Cohorte> cohortes = List.of(c1, c2, c3, c4, c0);

        // ------------------------------------------------------------ Comptes du personnel
        user("admin1", "Admin", "Système", RoleEnum.ROLE_ADMIN_SYSTEME, null, null, "+22360000001");
        user("adminsnj1", "Adama", "Sow", RoleEnum.ROLE_ADMIN_SNJ, null, null, "+22360000002");
        user("adminsnj2", "Mariam", "Kanté", RoleEnum.ROLE_ADMIN_SNJ, null, null, "+22360000003");
        user("direction1", "Général", "Dakouo", RoleEnum.ROLE_DIRECTION, null, null, "+22360000004");
        User regional1 = user("regional1", "Issa", "Bagayoko", RoleEnum.ROLE_RESPONSABLE_REGIONAL, "Bamako", null, "+22360000005");
        user("regional2", "Nana", "Kouyaté", RoleEnum.ROLE_RESPONSABLE_REGIONAL, "Ségou", null, "+22360000006");
        user("regional3", "Siaka", "Bengaly", RoleEnum.ROLE_RESPONSABLE_REGIONAL, "Sikasso", null, "+22360000007");
        User cohorte1 = user("cohorte1", "Karim", "Tangara", RoleEnum.ROLE_RESPONSABLE_COHORTE, null, c1.getId(), "+22360000008");
        User cohorte2 = user("cohorte2", "Aïssata", "Dicko", RoleEnum.ROLE_RESPONSABLE_COHORTE, null, c3.getId(), "+22360000009");
        user("cohorte3", "Bréhima", "Samaké", RoleEnum.ROLE_RESPONSABLE_COHORTE, null, c4.getId(), "+22360000010");
        user("partenaire1", "Orange", "Mali RH", RoleEnum.ROLE_PARTENAIRE, null, null, "+22360000011");
        user("partenaire2", "APEJ", "Recrutement", RoleEnum.ROLE_PARTENAIRE, null, null, "+22360000012");
        responsable(c1, cohorte1);
        responsable(c3, cohorte2);

        // ------------------------------------------------------------ Référentiel de compétences
        for (String[] c : COMPETENCES) {
            if (competenceReferentielRepository.findAll().stream().noneMatch(r -> r.getNom().equalsIgnoreCase(c[0])))
                competenceReferentielRepository.save(CompetenceReferentiel.builder().nom(c[0]).categorie(c[1])
                        .description("Compétence " + c[0].toLowerCase()).build());
        }

        // ------------------------------------------------------------ Membres (agent1 … agentN)
        List<Membre> membres = new ArrayList<>();
        for (int i = 1; i <= NB_AGENTS; i++) {
            boolean femme = i % 3 == 0;
            String prenom = femme ? PRENOMS_F[i % PRENOMS_F.length] : PRENOMS_H[i % PRENOMS_H.length];
            String nom = NOMS[i % NOMS.length];
            String tel = String.format("+2237%07d", i);
            User u = user("agent" + i, prenom, nom, RoleEnum.ROLE_MEMBRE, null, null, tel);
            Cohorte cohorte = cohortes.get(i <= 34 ? (i - 1) % 4 : 4);
            membres.add(membre(i, u, prenom, nom, femme, tel, cohorte));
        }

        // ------------------------------------------------------------ Activités & présences
        LocalDate auj = LocalDate.now();
        Activite a1 = activite("Formation continue — premiers secours", auj.minusDays(20), "Centre de Kati", "Koulikoro", StatutActivite.TERMINEE, null);
        Activite a2 = activite("Journée de salubrité — Commune IV", auj.minusDays(5), "Mairie de la Commune IV", "Bamako", StatutActivite.TERMINEE, null);
        Activite a3 = activite("Rassemblement trimestriel des réservistes", auj, "Stade Modibo Keïta", "Bamako", StatutActivite.EN_COURS, null);
        activite("Atelier insertion professionnelle", auj.plusDays(10), "Maison des jeunes de Ségou", "Ségou", StatutActivite.PLANIFIEE, null);
        activite("Campagne de reboisement", auj.plusDays(25), "Forêt classée de la Faya", "Koulikoro", StatutActivite.PLANIFIEE, null);
        for (int i = 0; i < 15; i++) presence(a1, membres.get(i), i % 3 == 0 ? ModePresence.MANUEL : ModePresence.QR_CODE);
        for (int i = 5; i < 22; i++) presence(a2, membres.get(i), ModePresence.QR_CODE);
        for (int i = 0; i < 6; i++) presence(a3, membres.get(i), ModePresence.CODE);

        // ------------------------------------------------------------ Mobilisations
        Mobilisation m1 = mobilisation("Appui à la campagne de vaccination", "Santé", auj.minusDays(30), auj.minusDays(25),
                "Bamako", 20, false, StatutMobilisation.CLOTUREE, "Secourisme,Soins infirmiers");
        Mobilisation m2 = mobilisation("Sécurisation du festival sur le Niger", "Sécurité / encadrement", auj.plusDays(3), auj.plusDays(6),
                "Ségou", 30, true, StatutMobilisation.EN_COURS, "Secourisme");
        mobilisation("Réhabilitation d'écoles — Sikasso", "BTP", auj.plusDays(15), auj.plusDays(30),
                "Sikasso", 15, false, StatutMobilisation.PLANIFIEE, "Maçonnerie,Menuiserie,Électricité bâtiment");
        mobilisation("Opération inondations (brouillon)", "Humanitaire", auj.plusDays(40), null,
                "Mopti", 25, true, StatutMobilisation.BROUILLON, null);
        ReponseMobilisation[] reps = ReponseMobilisation.values();
        for (int i = 0; i < 18; i++) participation(m1, membres.get(i), i < 14 ? ReponseMobilisation.DISPONIBLE : ReponseMobilisation.INDISPONIBLE);
        for (int i = 0; i < 25; i++) participation(m2, membres.get(i), reps[i % 3]);
        a1.setMobilisation(m1);
        activiteRepository.save(a1);

        // ------------------------------------------------------------ Opportunités & candidatures
        Opportunite o1 = opportunite(TypeOpportunite.EMPLOI, "Technicien réseau", "Orange Mali", "Bamako", "CDI", "Informatique bureautique,Développement web", StatutOpportunite.OUVERTE);
        Opportunite o2 = opportunite(TypeOpportunite.STAGE, "Stage assistant comptable", "BMS-SA", "Bamako", "Stage 6 mois", "Comptabilité", StatutOpportunite.OUVERTE);
        Opportunite o3 = opportunite(TypeOpportunite.FORMATION, "Formation certifiante en électricité solaire", "APEJ", "Ségou", "Formation 3 mois", "Électricité bâtiment", StatutOpportunite.OUVERTE);
        opportunite(TypeOpportunite.EMPLOI, "Chauffeur poids lourd", "Transports Diarra", "Kayes", "CDD 12 mois", "Conduite poids lourd", StatutOpportunite.CLOTUREE);
        opportunite(TypeOpportunite.EMPLOI, "Maçon chef d'équipe", "SOGEA-SATOM", "Sikasso", "CDD", "Maçonnerie", StatutOpportunite.BROUILLON);
        StatutCandidature[] sc = StatutCandidature.values();
        for (int i = 0; i < 8; i++) candidature(o1, membres.get(i), sc[i % sc.length]);
        for (int i = 8; i < 13; i++) candidature(o2, membres.get(i), StatutCandidature.NOUVELLE);
        for (int i = 13; i < 20; i++) candidature(o3, membres.get(i), sc[i % 3]);

        // ------------------------------------------------------------ Actualités
        actualite("Lancement de la Cohorte 2025", "Plus de 1 200 recrues accueillies à Kati", CategorieActualite.ANNONCE, StatutActualite.PUBLIEE, true, -10);
        actualite("Nouvelles offres d'emploi partenaires", "Orange Mali et l'APEJ recrutent", CategorieActualite.OPPORTUNITE, StatutActualite.PUBLIEE, false, -4);
        actualite("Mobilisation : festival sur le Niger", "Appel à volontaires pour Ségou", CategorieActualite.MOBILISATION, StatutActualite.PUBLIEE, false, -1);
        actualite("Rapport annuel 2025", "Bilan des activités du SNJ", CategorieActualite.RAPPORT, StatutActualite.BROUILLON, false, 0);
        actualite("Cérémonie de libération Cohorte 2024", "Programme de la cérémonie", CategorieActualite.ANNONCE, StatutActualite.PROGRAMMEE, false, 7);

        // ------------------------------------------------------------ Notifications
        notification("Bienvenue sur SNJPro", "Votre espace membre est disponible. Complétez votre profil.", TypeNotification.INFORMATION, membres, 20);
        notification("Mobilisation urgente — Ségou", "Merci de confirmer votre disponibilité avant demain 18h.", TypeNotification.URGENT, membres.subList(0, 25), 5);
        notification("Nouvelle actualité publiée", "Consultez les nouvelles offres d'emploi partenaires.", TypeNotification.ACTUALITE, membres, 2);

        // ------------------------------------------------------------ Demandes
        TypeDemande[] td = TypeDemande.values();
        StatutDemande[] sd = StatutDemande.values();
        for (int i = 0; i < 12; i++) demande(i + 1, membres.get(i * 3 % membres.size()), td[i % td.length], sd[i % sd.length]);

        // ------------------------------------------------------------ Messagerie
        Conversation directe = conversation(TypeConversation.DIRECTE, null, null, null, List.of(cohorte1.getId(), membres.get(0).getUserId()));
        message(directe, cohorte1.getId(), "Bonjour, pensez à mettre à jour votre situation professionnelle.", 60);
        message(directe, membres.get(0).getUserId(), "Bonjour chef, c'est fait. Merci !", 30);
        List<Long> groupeC1 = new ArrayList<>(List.of(cohorte1.getId()));
        membres.stream().filter(m -> m.getCohorte() == c1).forEach(m -> groupeC1.add(m.getUserId()));
        Conversation groupe = conversation(TypeConversation.GROUPE, c1.getNom(), c1.getId(), null, groupeC1);
        message(groupe, cohorte1.getId(), "Rassemblement samedi 8h au stade. Tenue obligatoire.", 120);
        message(groupe, membres.get(4).getUserId(), "Bien reçu.", 90);
        Conversation service = conversation(TypeConversation.SERVICE, "Direction régionale · Bamako", null, "Bamako",
                List.of(membres.get(1).getUserId(), regional1.getId()));
        message(service, membres.get(1).getUserId(), "Bonjour, comment obtenir une attestation de service ?", 200);
        message(service, regional1.getId(), "Faites une demande « Attestation » depuis votre espace, nous la traiterons.", 180);

        // ------------------------------------------------------------ Dossiers de documents
        for (String[] d : new String[][]{{"Textes officiels", "PUBLIC"}, {"Formulaires", "MEMBRES"}, {"Rapports internes", "INTERNE"}}) {
            if (dossierRepository.findAll().stream().noneMatch(x -> x.getNom().equals(d[0])))
                dossierRepository.save(Dossier.builder().nom(d[0]).visibiliteDefaut(VisibiliteDocument.valueOf(d[1])).build());
        }

        log.info("Jeu de données de test inséré : {} agents, {} cohortes. Mot de passe = nom d'utilisateur (ex. agent1 / agent1). "
                + "Autres comptes : admin1, adminsnj1, direction1, regional1, cohorte1, partenaire1.", NB_AGENTS, cohortes.size());    }

    // =====================================================================

    private User user(String username, String prenom, String nom, RoleEnum role, String region, Long cohorteId, String tel) {
        return userRepository.save(User.builder().username(username).password(passwordEncoder.encode(username))
                .email(username + "@snj.ml").prenom(prenom).nom(nom).role(role).telephone(tel)
                .region(region).cohorteId(cohorteId).doubleAuthentification(false).invitationEnAttente(false)
                .actif(true).createdBy("seed").build());
    }

    private Cohorte cohorte(String nom, String code, int numero, int debut, int fin, StatutCohorteEnum statut, String centre, String region) {
        return cohorteRepository.save(Cohorte.builder().nom(nom).code(code).numero(numero).anneeDebut(debut).anneeFin(fin)
                .dateDebut(LocalDate.of(debut, 10, 1)).dateFin(LocalDate.of(fin, 9, 30)).statut(statut).centre(centre)
                .region(region).recruesInitiales(800 + numero * 10).nombreCompagnies(4).nombreSections(16)
                .description("Promotion " + numero + " du Service National des Jeunes").build());
    }

    private void responsable(Cohorte c, User u) {
        c.setResponsableId(u.getId());
        c.setResponsable(u.getPrenom() + " " + u.getNom());
        cohorteRepository.save(c);
    }

    private Membre membre(int i, User u, String prenom, String nom, boolean femme, String tel, Cohorte cohorte) {
        String[] r = REGIONS[i % REGIONS.length];
        StatutMembreEnum statut = i == 38 ? StatutMembreEnum.INACTIF : i == 39 ? StatutMembreEnum.SUSPENDU
                : i == 40 ? StatutMembreEnum.ARCHIVE : StatutMembreEnum.ACTIF;
        Membre m = Membre.builder().identifiantSnj(String.format("SNJ-%d-%04d", cohorte.getId(), 100 + i))
                .prenom(prenom).nom(nom).sexe(femme ? SexeEnum.FEMININ : SexeEnum.MASCULIN)
                .dateNaissance(LocalDate.of(1993 + i % 10, 1 + i % 12, 1 + i % 28)).lieuNaissance(r[1])
                .numeroTelephone(tel).telephoneVerifie(i % 4 != 0).email(u.getEmail()).nationalite("Malienne")
                .groupeSanguin(GROUPES[i % GROUPES.length]).taille(1.60 + (i % 30) / 100.0).poids(55.0 + i % 30)
                .dernierDiplome(DIPLOMES[i % DIPLOMES.length]).categorie(i % 5 == 0 ? "FONCTIONNAIRE" : "NON_FONCTIONNAIRE")
                .compagnie("Compagnie " + (1 + i % 4)).section("Section " + (1 + i % 16))
                .dateIncorporation(cohorte.getDateDebut()).disponibleMobilisation(i % 6 != 0)
                .personneAPrevenir("Parent de " + prenom + " — +2236600" + String.format("%04d", i))
                .statut(statut).cohorte(cohorte).userId(u.getId()).createdBy("seed").build();

        m.setLocalisation(Localisation.builder().membre(m).pays("Mali").region(r[0]).cercle(r[1]).commune(r[2])
                .quartier(r[3]).adresseComplete(r[3] + ", " + r[2] + ", " + r[0]).build());

        StatutProfessionnelEnum sp = StatutProfessionnelEnum.values()[i % 5];
        String[] comp = COMPETENCES[i % COMPETENCES.length];
        m.setSituationProfessionnelle(SituationProfessionnelle.builder().membre(m).statut(sp)
                .profession(sp == StatutProfessionnelEnum.EMPLOYE || sp == StatutProfessionnelEnum.AUTO_EMPLOYE ? comp[0] : null)
                .secteurActivite(comp[1]).nomEmployeur(sp == StatutProfessionnelEnum.EMPLOYE ? "Entreprise " + nom : null)
                .dateDebutActivite(sp == StatutProfessionnelEnum.EMPLOYE ? LocalDate.of(2024, 1 + i % 12, 1) : null).build());

        NiveauCompetenceEnum[] niv = NiveauCompetenceEnum.values();
        for (int k = 0; k < 1 + i % 3; k++) {
            String[] c = COMPETENCES[(i + k * 5) % COMPETENCES.length];
            m.getCompetences().add(MembreCompetence.builder().membre(m).intitule(c[0]).categorie(c[1])
                    .niveau(niv[(i + k) % niv.length]).build());
        }

        m.getParcours().add(ParcoursSNJ.builder().membre(m).intitule("Formation militaire initiale").type("Formation")
                .dateDebut(cohorte.getDateDebut()).dateFin(cohorte.getDateDebut().plusMonths(3)).lieu(cohorte.getCentre()).build());
        m.getParcours().add(ParcoursSNJ.builder().membre(m).intitule("Formation professionnelle — " + comp[0]).type("Formation professionnelle")
                .dateDebut(cohorte.getDateDebut().plusMonths(4)).dateFin(cohorte.getDateDebut().plusMonths(9)).lieu(cohorte.getCentre()).build());
        if (i % 2 == 0)
            m.getParcours().add(ParcoursSNJ.builder().membre(m).intitule("Stage pratique").type("Stage")
                    .dateDebut(cohorte.getDateDebut().plusMonths(9)).dateFin(cohorte.getDateDebut().plusMonths(11)).lieu(r[0]).build());
        return membreRepository.save(m);
    }

    private Activite activite(String titre, LocalDate date, String lieu, String region, StatutActivite statut, Mobilisation mob) {
        byte[] secret = new byte[16];
        random.nextBytes(secret);
        return activiteRepository.save(Activite.builder().titre(titre).description(titre + " — activité organisée par le SNJ.")
                .date(date).heureDebut(LocalTime.of(8, 0)).heureFin(LocalTime.of(13, 0)).lieu(lieu).region(region)
                .mobilisation(mob).secretQr(HexFormat.of().formatHex(secret)).statut(statut).creePar(AUTEUR).build());
    }

    private void presence(Activite a, Membre m, ModePresence mode) {
        presenceRepository.save(Presence.builder().activite(a).membre(m).mode(mode)
                .dateHeure(a.getDate().atTime(8, random.nextInt(50))).saisiePar(mode == ModePresence.MANUEL ? AUTEUR : null).build());
    }

    private Mobilisation mobilisation(String titre, String besoin, LocalDate debut, LocalDate fin, String region, int nb,
                                      boolean urgent, StatutMobilisation statut, String competences) {
        boolean envoyee = statut != StatutMobilisation.BROUILLON;
        return mobilisationRepository.save(Mobilisation.builder().titre(titre).description(titre + ". Les volontaires seront encadrés par les responsables régionaux.")
                .typeBesoin(besoin).organisateur("Direction nationale du SNJ").dateDebut(debut).dateFin(fin)
                .heureRendezVous(LocalTime.of(7, 30)).lieu("Siège régional — " + region).region(region).nombreVolontaires(nb)
                .dateLimiteReponse(debut.minusDays(1)).priseEnCharge("Transport,Restauration").urgent(urgent)
                .critereRegions(region).critereCompetences(competences).critereStatuts("ACTIF")
                .message("Vous êtes sollicité(e) pour : " + titre + ". Merci de répondre rapidement.")
                .canalApplication(true).canalSms(urgent).statut(statut).creePar(AUTEUR)
                .dateEnvoi(envoyee ? debut.minusDays(7).atTime(9, 0) : null)
                .dateCloture(statut == StatutMobilisation.CLOTUREE ? fin.atTime(18, 0) : null).build());
    }

    private void participation(Mobilisation mob, Membre m, ReponseMobilisation rep) {
        LocalDateTime sollicitation = mob.getDateEnvoi() != null ? mob.getDateEnvoi() : LocalDateTime.now();
        participationRepository.save(ParticipationMobilisation.builder().mobilisation(mob).membre(m).reponse(rep)
                .motifIndisponibilite(rep == ReponseMobilisation.INDISPONIBLE ? "Indisponible pour raisons professionnelles" : null)
                .dateReponse(rep == ReponseMobilisation.EN_ATTENTE ? null : sollicitation.plusHours(3))
                .dateSollicitation(sollicitation).relances(rep == ReponseMobilisation.EN_ATTENTE ? 1 : 0).build());
    }

    private Opportunite opportunite(TypeOpportunite type, String intitule, String org, String lieu, String contrat,
                                    String competences, StatutOpportunite statut) {
        return opportuniteRepository.save(Opportunite.builder().type(type).intitule(intitule).organisation(org).lieu(lieu)
                .typeContrat(contrat).dateLimite(LocalDate.now().plusDays(statut == StatutOpportunite.CLOTUREE ? -5 : 30))
                .description(intitule + " chez " + org + ". Poste ouvert aux membres du SNJ.").competences(competences)
                .statut(statut).region(lieu).notifierMembres(true).portailPublic(statut == StatutOpportunite.OUVERTE)
                .membresNotifies(12).vues(random.nextInt(300)).publiePar(AUTEUR)
                .datePublication(statut == StatutOpportunite.BROUILLON ? null : LocalDateTime.now().minusDays(7)).build());
    }

    private void candidature(Opportunite o, Membre m, StatutCandidature s) {
        candidatureRepository.save(Candidature.builder().opportunite(o).membre(m).statut(s).score(50 + random.nextInt(50))
                .message("Je suis motivé(e) par ce poste.").dateCandidature(LocalDateTime.now().minusDays(random.nextInt(6) + 1)).build());
    }

    private void actualite(String titre, String sousTitre, CategorieActualite cat, StatutActualite statut, boolean une, int jours) {
        actualiteRepository.save(Actualite.builder().titre(titre).sousTitre(sousTitre)
                .contenu("<p>" + sousTitre + ".</p><p>Retrouvez toutes les informations auprès de votre direction régionale.</p>")
                .categorie(cat).statut(statut).datePublication(statut == StatutActualite.BROUILLON ? null : LocalDateTime.now().plusDays(jours))
                .cible(CibleNotification.TOUS).notifierMembres(statut == StatutActualite.PUBLIEE).epingle(une)
                .inscriptionsOuvertes(cat == CategorieActualite.MOBILISATION).vues(statut == StatutActualite.PUBLIEE ? random.nextInt(500) : 0)
                .auteur(AUTEUR).build());
    }

    private void notification(String titre, String message, TypeNotification type, List<Membre> dest, int joursAvant) {
        Notification n = notificationRepository.save(Notification.builder().titre(titre).message(message).type(type)
                .cible(CibleNotification.TOUS).canalApplication(true).canalSms(type == TypeNotification.URGENT)
                .statut(StatutNotification.ENVOYEE).dateEnvoi(LocalDateTime.now().minusDays(joursAvant))
                .nombreDestinataires(dest.size()).nombreSms(type == TypeNotification.URGENT ? dest.size() : 0).emetteur(AUTEUR).build());
        for (int i = 0; i < dest.size(); i++) {
            boolean lu = i % 2 == 0;
            notificationDestinataireRepository.save(NotificationDestinataire.builder().notification(n).membre(dest.get(i))
                    .lu(lu).dateLecture(lu ? n.getDateEnvoi().plusHours(2) : null).build());
        }
    }

    private void demande(int n, Membre m, TypeDemande type, StatutDemande statut) {
        String objet = switch (type) {
            case MISE_A_JOUR_PROFIL -> "Changement d'adresse";
            case ATTESTATION -> "Attestation de service national";
            case CARTE_MEMBRE -> "Renouvellement de carte de membre";
            case RECLAMATION -> "Erreur sur ma date d'incorporation";
            case AUTRE -> "Demande d'information";
        };
        boolean traitee = statut == StatutDemande.ACCEPTEE || statut == StatutDemande.REJETEE;
        boolean prise = statut != StatutDemande.EN_ATTENTE;
        LocalDateTime creation = LocalDateTime.now().minusDays(15 - n);
        Demande d = demandeRepository.save(Demande.builder()
                .numeroSuivi(String.format("DEM-%d-T%03d", LocalDate.now().getYear(), n)).membre(m).type(type).objet(objet)
                .description(objet + " — demande créée pour les tests.").statut(statut)
                .modifications(type == TypeDemande.MISE_A_JOUR_PROFIL ? "{\"quartier\":\"Hamdallaye\"}" : null)
                .reponse(statut == StatutDemande.ACCEPTEE ? "Votre demande a été acceptée." : null)
                .motifRejet(statut == StatutDemande.REJETEE ? "Pièce justificative manquante" : null)
                .elementsDemandes(statut == StatutDemande.COMPLEMENT_DEMANDE ? "Copie de la pièce d'identité" : null)
                .dateLimiteComplement(statut == StatutDemande.COMPLEMENT_DEMANDE ? LocalDate.now().plusDays(7) : null)
                .traitePar(prise ? AUTEUR : null).datePriseEnCharge(prise ? creation.plusDays(1) : null)
                .dateTraitement(traitee ? creation.plusDays(2) : null).build());
        evenement(d, "Demande soumise", m.getPrenom() + " " + m.getNom(), creation);
        if (prise) evenement(d, "Prise en charge", AUTEUR, creation.plusDays(1));
        if (statut == StatutDemande.COMPLEMENT_DEMANDE) evenement(d, "Complément demandé", AUTEUR, creation.plusDays(1).plusHours(2));
        if (traitee) evenement(d, statut == StatutDemande.ACCEPTEE ? "Demande acceptée" : "Demande rejetée", AUTEUR, creation.plusDays(2));
    }

    private void evenement(Demande d, String libelle, String auteur, LocalDateTime date) {
        demandeEvenementRepository.save(DemandeEvenement.builder().demande(d).libelle(libelle).auteur(auteur).date(date).build());
    }

    private Conversation conversation(TypeConversation type, String titre, Long cohorteId, String region, List<Long> userIds) {
        Conversation c = conversationRepository.save(Conversation.builder().type(type).titre(titre).cohorteId(cohorteId)
                .regionService(region).dernierMessageLe(LocalDateTime.now()).build());
        userIds.forEach(id -> participantRepository.save(ParticipantConversation.builder().conversation(c).userId(id).build()));
        return c;
    }

    private void message(Conversation c, Long auteur, String contenu, int minutesAvant) {
        messageRepository.save(Message.builder().conversation(c).auteurId(auteur).contenu(contenu)
                .dateEnvoi(LocalDateTime.now().minusMinutes(minutesAvant)).build());
    }
}
