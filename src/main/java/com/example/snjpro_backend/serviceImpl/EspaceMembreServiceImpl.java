package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.dto.EspaceMembreDTOs.*;
import com.example.snjpro_backend.dto.MobilisationDTOs.MaMobilisation;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutCandidature;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.repository.ParticipationMobilisationRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EspaceMembreServiceImpl implements EspaceMembreServiceInterface {

    private static final String PREFIXE_QR = "SNJPRO:MBR:";
    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PerimetreService perimetre;
    private final MembreRepository membreRepository;
    private final MembreServiceInterface membreService;
    private final MobilisationServiceInterface mobilisationService;
    private final ActualiteServiceInterface actualiteService;
    private final ActiviteServiceInterface activiteService;
    private final NotificationServiceInterface notificationService;
    private final MessagerieServiceInterface messagerieService;
    private final DemandeServiceInterface demandeService;
    private final OpportuniteServiceInterface opportuniteService;
    private final GenerateurFichiers generateur;
    private final ParticipationMobilisationRepository participationRepository;

    @Value("${app.jwt.secret}")
    private String secret;

    @Override
    public Accueil accueil() {
        Long membreId = perimetre.membreIdCourant();
        List<MaMobilisation> mobilisations = mobilisationService.mesMobilisations();
        MaMobilisation enCours = mobilisations.stream()
                .filter(m -> m.getStatut() == StatutMobilisation.EN_COURS)
                .findFirst().orElse(null);
        return Accueil.builder()
                .membre(membreService.obtenirMembre(membreId))
                .completude(completude(membreId))
                .mobilisationEnCours(enCours)
                .mobilisationsEnAttenteDeReponse(mobilisations.stream()
                        .filter(m -> m.getStatut() == StatutMobilisation.EN_COURS
                                && (m.getMaReponse() == null || m.getMaReponse() == ReponseMobilisation.EN_ATTENTE))
                        .count())
                .aLaUne(actualiteService.aLaUne())
                .actualitesRecentes(actualiteService.fil(null).stream().limit(5).toList())
                .activitesDuJour(activiteService.activitesDuJour())
                .notificationsNonLues(notificationService.compterNonLues())
                .messagesNonLus(messagerieService.nonLus())
                .demandesEnCours(demandeService.mesDemandes(false).size())
                .candidaturesEnCours(opportuniteService.mesCandidatures().stream()
                        .filter(c -> c.getStatut() != StatutCandidature.RETENUE && c.getStatut() != StatutCandidature.NON_RETENUE)
                        .count())
                .build();
    }

    @Override
    public Completude completude(Long membreId) {
        Membre m = trouver(membreId);
        return Completude.builder()
                .taux(CompletudeProfil.taux(m))
                .complet(CompletudeProfil.complet(m))
                .elementsManquants(CompletudeProfil.manquants(m))
                .build();
    }

    @Override
    public Carte carte(Long membreId) {
        Membre m = trouver(membreId);
        if (m.getStatut() == StatutMembreEnum.ARCHIVE)
            throw new IllegalArgumentException("La carte d'un membre archivé n'est plus valide.");
        return Carte.builder()
                .membreId(m.getId())
                .identifiantSnj(m.getIdentifiantSnj())
                .prenom(m.getPrenom())
                .nom(m.getNom())
                .photoUrl(m.getPhotoUrl())
                .dateNaissance(m.getDateNaissance())
                .groupeSanguin(m.getGroupeSanguin())
                .cohorte(m.getCohorte() == null ? null : m.getCohorte().getNom())
                .compagnie(m.getCompagnie())
                .section(m.getSection())
                .dateIncorporation(m.getDateIncorporation())
                .statut(m.getStatut() == null ? null : m.getStatut().name())
                .region(m.getLocalisation() == null ? null : m.getLocalisation().getRegion())
                .qrContenu(PREFIXE_QR + m.getIdentifiantSnj() + ":" + signature(m.getIdentifiantSnj()))
                .dateEmission(LocalDate.now())
                .build();
    }

    @Override
    public VerificationCarte verifierCarte(String qrContenu) {
        String contenu = qrContenu == null ? "" : qrContenu.trim();
        if (!contenu.startsWith(PREFIXE_QR))
            return VerificationCarte.builder().valide(false).message("QR code non reconnu : ce n'est pas une carte SNJPro.").build();
        String[] parties = contenu.substring(PREFIXE_QR.length()).split(":");
        if (parties.length != 2 || !MessageDigest.isEqual(signature(parties[0]).getBytes(StandardCharsets.UTF_8),
                parties[1].getBytes(StandardCharsets.UTF_8)))
            return VerificationCarte.builder().valide(false).message("Signature invalide : carte falsifiée ou altérée.").build();
        Optional<Membre> membre = membreRepository.findByIdentifiantSnj(parties[0]);
        if (membre.isEmpty())
            return VerificationCarte.builder().valide(false).identifiantSnj(parties[0]).message("Aucun membre ne porte cet identifiant.").build();
        Membre m = membre.get();
        boolean actif = m.getStatut() != StatutMembreEnum.ARCHIVE;
        return VerificationCarte.builder()
                .valide(actif)
                .message(actif ? "Carte authentique." : "Carte authentique mais le membre est archivé.")
                .membreId(perimetre.peutAcceder(m.getId()) ? m.getId() : null)
                .identifiantSnj(m.getIdentifiantSnj())
                .nomComplet(m.getPrenom() + " " + m.getNom())
                .photoUrl(m.getPhotoUrl())
                .cohorte(m.getCohorte() == null ? null : m.getCohorte().getNom())
                .statut(m.getStatut() == null ? null : m.getStatut().name())
                .build();
    }

    @Override
    public Cv cv(Long membreId) {
        ProfilMembreDTO profil = membreService.obtenirProfilComplet(membreId);
        MembreResponseDTO m = profil.getMembre();
        SituationProfessionnelleDTO sp = m.getSituationProfessionnelle();
        String titre = sp != null && StringUtils.hasText(sp.getProfession()) ? sp.getProfession() : "Membre du Service National des Jeunes";
        List<String> engagements = new ArrayList<>();
        if (m.getCohorteNom() != null)
            engagements.add("Service National des Jeunes — " + m.getCohorteNom()
                    + (m.getDateIncorporation() == null ? "" : " (incorporé le " + m.getDateIncorporation().format(JJ_MM_AAAA) + ")"));
        participationRepository.findByMembreIdOrderByDateSollicitationDesc(membreId).stream()
                .filter(p -> p.getReponse() == ReponseMobilisation.DISPONIBLE)
                .map(p -> p.getMobilisation())
                .forEach(mo -> engagements.add("Mobilisation « " + mo.getTitre() + " »"
                        + (mo.getDateDebut() == null ? "" : " — " + mo.getDateDebut().format(JJ_MM_AAAA))
                        + (StringUtils.hasText(mo.getLieu()) ? ", " + mo.getLieu() : "")));
        return Cv.builder().membre(m).titre(titre).competences(profil.getCompetences())
                .parcours(profil.getParcours()).engagements(engagements).build();
    }

    @Override
    public byte[] cvPdf(Long membreId) {
        Cv cv = cv(membreId);
        MembreResponseDTO m = cv.getMembre();
        List<GenerateurFichiers.Section> sections = new ArrayList<>();
        List<String[]> identite = new ArrayList<>();
        identite.add(new String[]{"Identifiant SNJ", m.getIdentifiantSnj()});
        if (m.getDateNaissance() != null)
            identite.add(new String[]{"Né(e) le", m.getDateNaissance().format(JJ_MM_AAAA)
                    + (m.getLieuNaissance() == null ? "" : " à " + m.getLieuNaissance())});
        identite.add(new String[]{"Téléphone", m.getNumeroTelephone()});
        identite.add(new String[]{"Email", m.getEmail()});
        if (m.getLocalisation() != null)
            identite.add(new String[]{"Adresse", joindre(m.getLocalisation().getQuartier(), m.getLocalisation().getCommune(),
                    m.getLocalisation().getCercle(), m.getLocalisation().getRegion())});
        if (m.getDernierDiplome() != null) identite.add(new String[]{"Dernier diplôme", m.getDernierDiplome()});
        sections.add(new GenerateurFichiers.Section("Informations personnelles", identite));

        SituationProfessionnelleDTO sp = m.getSituationProfessionnelle();
        if (sp != null) {
            List<String[]> pro = new ArrayList<>();
            pro.add(new String[]{"Statut", sp.getStatut() == null ? null : sp.getStatut().name()});
            if (sp.getProfession() != null) pro.add(new String[]{"Profession", sp.getProfession()});
            if (sp.getSecteurActivite() != null) pro.add(new String[]{"Secteur", sp.getSecteurActivite()});
            if (sp.getNomEmployeur() != null) pro.add(new String[]{"Employeur", sp.getNomEmployeur()});
            if (sp.getDescription() != null) pro.add(new String[]{sp.getDescription()});
            sections.add(new GenerateurFichiers.Section("Situation professionnelle", pro));
        }
        if (!cv.getCompetences().isEmpty()) {
            List<String[]> comp = new ArrayList<>();
            cv.getCompetences().forEach(c -> comp.add(new String[]{c.getIntitule(),
                    joindre(c.getCategorie(), c.getNiveau() == null ? null : c.getNiveau().name())}));
            sections.add(new GenerateurFichiers.Section("Compétences", comp));
        }
        if (!cv.getParcours().isEmpty()) {
            List<String[]> parc = new ArrayList<>();
            cv.getParcours().forEach(p -> parc.add(new String[]{p.getIntitule(), joindre(
                    p.getDateDebut() == null ? null : p.getDateDebut().format(JJ_MM_AAAA)
                            + (p.getDateFin() == null ? "" : " → " + p.getDateFin().format(JJ_MM_AAAA)),
                    p.getLieu(), p.getDescription())}));
            sections.add(new GenerateurFichiers.Section("Parcours SNJ", parc));
        }
        if (!cv.getEngagements().isEmpty()) {
            List<String[]> eng = new ArrayList<>();
            cv.getEngagements().forEach(e -> eng.add(new String[]{"• " + e}));
            sections.add(new GenerateurFichiers.Section("Engagements", eng));
        }
        return generateur.document(m.getPrenom() + " " + m.getNom(), cv.getTitre(), sections,
                "CV généré le " + LocalDate.now().format(JJ_MM_AAAA) + " depuis SNJPro.");
    }

    private Membre trouver(Long id) {
        return membreRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + id));
    }

    /** Signature courte (HMAC-SHA256) empêchant de fabriquer une carte avec un identifiant quelconque. */
    private String signature(String identifiant) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(("CARTE:" + identifiant).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String joindre(String... valeurs) {
        StringJoiner j = new StringJoiner(" · ");
        for (String v : valeurs) if (StringUtils.hasText(v)) j.add(v);
        return j.toString();
    }
}
