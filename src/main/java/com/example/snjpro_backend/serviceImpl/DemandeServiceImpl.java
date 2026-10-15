package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.DemandeDTOs.*;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.*;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.DocumentServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import com.example.snjpro_backend.serviceInterface.DemandeServiceInterface;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DemandeServiceImpl implements DemandeServiceInterface {
    private static final String ENTITE = "DEMANDE";
    private static final Set<String> CHAMPS = Set.of("region", "cercle", "commune", "quartier", "adresse", "profession",
            "employeur", "secteurActivite", "email", "telephone", "dateNaissance", "lieuNaissance", "dernierDiplome", "nom", "prenom");

    private final DemandeRepository demandeRepository;
    private final DemandeEvenementRepository evenementRepository;
    private final MembreRepository membreRepository;
    private final DocumentServiceInterface documentService;
    private final StockageFichierService stockage;
    private final NotificationServiceInterface notificationService;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;
    private final ObjectMapper objectMapper;

    // =====================================================================
    // Traitement
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> lister(StatutDemande statut, TypeDemande type, String recherche) {
        String q = StringUtils.hasText(recherche) ? recherche.trim().toLowerCase() : null;
        return demandeRepository.findAllByOrderByDateCreationDesc().stream()
                .filter(d -> perimetre.peutAcceder(d.getMembre().getId()))
                .filter(d -> statut == null || d.getStatut() == statut)
                .filter(d -> type == null || d.getType() == type)
                .filter(d -> q == null || (d.getNumeroSuivi() + " " + d.getObjet() + " " + d.getMembre().getPrenom() + " "
                        + d.getMembre().getNom()).toLowerCase().contains(q))
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Statistiques statistiques() {
        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Demande> toutes = demandeRepository.findAll();
        OptionalDouble delai = toutes.stream().filter(d -> d.getDateTraitement() != null)
                .mapToLong(d -> Duration.between(d.getDateCreation(), d.getDateTraitement()).toHours()).average();
        return Statistiques.builder()
                .enAttente(demandeRepository.countByStatut(StatutDemande.EN_ATTENTE))
                .enCours(demandeRepository.countByStatut(StatutDemande.EN_COURS))
                .complementsDemandes(demandeRepository.countByStatut(StatutDemande.COMPLEMENT_DEMANDE))
                .traiteesCeMois(toutes.stream().filter(d -> d.getDateTraitement() != null && d.getDateTraitement().isAfter(debutMois)).count())
                .delaiMoyenJours(delai.isPresent() ? Math.round(delai.getAsDouble() / 2.4) / 10.0 : null)
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Detail obtenir(Long id) { return toDetail(trouverGerable(id)); }

    @Override
    public Detail prendreEnCharge(Long id) {
        Demande d = trouverGerable(id);
        if (d.getStatut() != StatutDemande.EN_ATTENTE && d.getStatut() != StatutDemande.COMPLEMENT_DEMANDE)
            throw new ConflitException("Cette demande est déjà " + d.getStatut().name().toLowerCase().replace('_', ' ') + ".");
        User agent = perimetre.utilisateurCourant();
        d.setStatut(StatutDemande.EN_COURS);
        d.setTraitePar(agent.getUsername());
        d.setDatePriseEnCharge(LocalDateTime.now());
        evenement(d, "En traitement · " + nomComplet(agent), null);
        return toDetail(demandeRepository.save(d));
    }

    @Override
    public Detail valider(Long id, Validation dto) {
        Demande d = trouverOuverte(id);
        if (dto.isAppliquerModification() && d.getType() == TypeDemande.MISE_A_JOUR_PROFIL) appliquerModifications(d);
        cloturer(d, StatutDemande.ACCEPTEE, dto.getReponse());
        evenement(d, "Demande acceptée", dto.getReponse());
        notificationService.notifierMembres(List.of(d.getMembre()), TypeNotification.INFORMATION, "Demande traitée",
                "Votre demande « " + d.getObjet() + " » (" + d.getNumeroSuivi() + ") a été acceptée.", false, "DEMANDE", id);
        historiqueService.enregistrer("VALIDATION", ENTITE, id, d.getNumeroSuivi());
        return toDetail(demandeRepository.save(d));
    }

    @Override
    public Detail rejeter(Long id, Rejet dto) {
        Demande d = trouverOuverte(id);
        d.setMotifRejet(dto.getMotif());
        cloturer(d, StatutDemande.REJETEE, dto.getMessage());
        evenement(d, "Demande rejetée : " + dto.getMotif(), dto.getMessage());
        notificationService.notifierMembres(List.of(d.getMembre()), TypeNotification.INFORMATION, "Demande rejetée",
                "Votre demande « " + d.getObjet() + " » a été rejetée : " + dto.getMotif() + ".", false, "DEMANDE", id);
        historiqueService.enregistrer("REJET", ENTITE, id, d.getNumeroSuivi() + " · " + dto.getMotif());
        return toDetail(demandeRepository.save(d));
    }

    @Override
    public Detail demanderComplement(Long id, Complement dto) {
        Demande d = trouverOuverte(id);
        d.setStatut(StatutDemande.COMPLEMENT_DEMANDE);
        d.setElementsDemandes(String.join("\n", dto.getElements()));
        d.setDateLimiteComplement(LocalDate.now().plusDays(dto.getDelaiJours()));
        d.setReponse(dto.getMessage());
        if (d.getTraitePar() == null) d.setTraitePar(perimetre.utilisateurCourant().getUsername());
        evenement(d, "Complément demandé", String.join(" ; ", dto.getElements()));
        notificationService.notifierMembres(List.of(d.getMembre()), TypeNotification.INFORMATION, "Complément demandé",
                "Des éléments sont nécessaires pour traiter votre demande " + d.getNumeroSuivi() + " : "
                        + String.join(", ", dto.getElements()) + ".", true, "DEMANDE", id);
        historiqueService.enregistrer("COMPLEMENT", ENTITE, id, d.getNumeroSuivi());
        return toDetail(demandeRepository.save(d));
    }

    @Override @Transactional(readOnly = true)
    public Fichier pieceJointe(Long id) {
        Demande d = trouverGerable(id);
        if (d.getPieceJointe() == null) throw new EntityNotFoundException("Cette demande n'a pas de pièce jointe.");
        Document p = d.getPieceJointe();
        return new Fichier(stockage.lire(p.getChemin()), p.getNomFichier(), p.getTypeMime());
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> mesDemandes(Boolean traitees) {
        return demandeRepository.findByMembreIdOrderByDateCreationDesc(perimetre.membreIdCourant()).stream()
                .filter(d -> traitees == null || traitees == estTraitee(d))
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Detail maDemande(Long id) { return toDetail(maDemandeEntite(id)); }

    @Override
    public Detail creer(Nouvelle dto, MultipartFile fichier) {
        Membre m = membreRepository.findById(perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Fiche membre introuvable."));
        List<Modification> modifs = new ArrayList<>();
        for (Modification x : dto.getModifications()) {
            if (!CHAMPS.contains(x.getChamp()))
                throw new IllegalArgumentException("Champ non modifiable par demande : " + x.getChamp());
            modifs.add(Modification.builder().champ(x.getChamp()).ancienne(valeurActuelle(m, x.getChamp())).nouvelle(x.getNouvelle()).build());
        }
        if (dto.getType() == TypeDemande.MISE_A_JOUR_PROFIL && modifs.isEmpty() && !StringUtils.hasText(dto.getDescription()))
            throw new IllegalArgumentException("Précisez les informations à mettre à jour.");
        Demande d = Demande.builder()
                .numeroSuivi(prochainNumero()).membre(m).type(dto.getType())
                .objet(dto.getObjet().trim()).description(dto.getDescription())
                .modifications(modifs.isEmpty() ? null : json(modifs))
                .build();
        if (fichier != null && !fichier.isEmpty())
            d.setPieceJointe(documentService.enregistrerPieceMembre(m, fichier, TypeDocument.AUTRE, "Pièce jointe " + d.getNumeroSuivi()));
        d = demandeRepository.save(d);
        evenement(d, "Demande envoyée", null);
        String region = m.getLocalisation() != null ? m.getLocalisation().getRegion() : null;
        evenement(d, region != null ? "Reçue par la direction régionale de " + region : "Reçue par la direction du SNJ", null);
        notificationService.alerterRoles(List.of(RoleEnum.ROLE_ADMIN_SNJ, RoleEnum.ROLE_RESPONSABLE_REGIONAL), region,
                "Nouvelle demande", m.getPrenom() + " " + m.getNom() + " · " + d.getObjet(), "DEMANDE", "DEMANDE", d.getId());
        notificationService.alerterResponsablesCohorte(m.getCohorte().getId(),
                "Nouvelle demande", m.getPrenom() + " " + m.getNom() + " · " + d.getObjet(), "DEMANDE", "DEMANDE", d.getId());
        historiqueService.enregistrer("CREATION", ENTITE, d.getId(), d.getNumeroSuivi() + " · " + d.getObjet());
        return toDetail(d);
    }

    @Override
    public Detail completer(Long id, String message, MultipartFile fichier) {
        Demande d = maDemandeEntite(id);
        if (d.getStatut() != StatutDemande.COMPLEMENT_DEMANDE)
            throw new ConflitException("Aucun complément n'est attendu pour cette demande.");
        if (fichier != null && !fichier.isEmpty())
            d.setPieceJointe(documentService.enregistrerPieceMembre(d.getMembre(), fichier, TypeDocument.AUTRE, "Complément " + d.getNumeroSuivi()));
        d.setStatut(StatutDemande.EN_COURS);
        evenement(d, "Complément envoyé par le membre", message);
        if (d.getTraitePar() != null)
        {
            notificationService.alerterRoles(List.of(RoleEnum.ROLE_ADMIN_SNJ), null, "Complément reçu",
                    d.getNumeroSuivi() + " · " + d.getObjet(), "DEMANDE", "DEMANDE", id);
            notificationService.alerterResponsablesCohorte(d.getMembre().getCohorte().getId(), "Complément reçu",
                    d.getNumeroSuivi() + " · " + d.getObjet(), "DEMANDE", "DEMANDE", id);
        }
        return toDetail(demandeRepository.save(d));
    }

    @Override
    public void annuler(Long id) {
        Demande d = maDemandeEntite(id);
        if (d.getStatut() != StatutDemande.EN_ATTENTE)
            throw new ConflitException("Seule une demande pas encore prise en charge peut être annulée.");
        evenementRepository.deleteAll(evenementRepository.findByDemandeIdOrderByDateAsc(id));
        demandeRepository.delete(d);
        historiqueService.enregistrer("ANNULATION", ENTITE, id, d.getNumeroSuivi());
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void appliquerModifications(Demande d) {
        Membre m = d.getMembre();
        for (Modification x : modifications(d)) {
            String v = StringUtils.hasText(x.getNouvelle()) ? x.getNouvelle().trim() : null;
            switch (x.getChamp()) {
                case "region", "cercle", "commune", "quartier", "adresse" -> {
                    Localisation l = m.getLocalisation();
                    if (l == null) {
                        l = Localisation.builder().membre(m).pays("Mali").build();
                        m.setLocalisation(l);
                    }
                    switch (x.getChamp()) {
                        case "region" -> l.setRegion(v);
                        case "cercle" -> l.setCercle(v);
                        case "commune" -> l.setCommune(v);
                        case "quartier" -> l.setQuartier(v);
                        default -> l.setAdresseComplete(v);
                    }
                }
                case "profession", "employeur", "secteurActivite" -> {
                    SituationProfessionnelle s = m.getSituationProfessionnelle();
                    if (s == null) {
                        s = SituationProfessionnelle.builder().membre(m).build();
                        m.setSituationProfessionnelle(s);
                    }
                    switch (x.getChamp()) {
                        case "profession" -> s.setProfession(v);
                        case "employeur" -> s.setNomEmployeur(v);
                        default -> s.setSecteurActivite(v);
                    }
                }
                case "email" -> m.setEmail(v == null ? null : v.toLowerCase());
                case "telephone" -> {
                    m.setNumeroTelephone(DoublonDetecteur.normaliserTelephone(v));
                    m.setTelephoneVerifie(false);
                }
                case "dateNaissance" -> m.setDateNaissance(v == null ? null : LocalDate.parse(v));
                case "lieuNaissance" -> m.setLieuNaissance(v);
                case "dernierDiplome" -> m.setDernierDiplome(v);
                case "nom" -> { if (v != null) m.setNom(v); }
                case "prenom" -> { if (v != null) m.setPrenom(v); }
                default -> { }
            }
        }
        membreRepository.save(m);
        historiqueService.enregistrer("MODIFICATION", "MEMBRE", m.getId(), "Suite à la demande " + d.getNumeroSuivi());
    }

    private String valeurActuelle(Membre m, String champ) {
        Localisation l = m.getLocalisation();
        SituationProfessionnelle s = m.getSituationProfessionnelle();
        return switch (champ) {
            case "region" -> l == null ? null : l.getRegion();
            case "cercle" -> l == null ? null : l.getCercle();
            case "commune" -> l == null ? null : l.getCommune();
            case "quartier" -> l == null ? null : l.getQuartier();
            case "adresse" -> l == null ? null : l.getAdresseComplete();
            case "profession" -> s == null ? null : s.getProfession();
            case "employeur" -> s == null ? null : s.getNomEmployeur();
            case "secteurActivite" -> s == null ? null : s.getSecteurActivite();
            case "email" -> m.getEmail();
            case "telephone" -> m.getNumeroTelephone();
            case "dateNaissance" -> m.getDateNaissance() == null ? null : m.getDateNaissance().toString();
            case "lieuNaissance" -> m.getLieuNaissance();
            case "dernierDiplome" -> m.getDernierDiplome();
            case "nom" -> m.getNom();
            case "prenom" -> m.getPrenom();
            default -> null;
        };
    }

    private void cloturer(Demande d, StatutDemande statut, String reponse) {
        d.setStatut(statut);
        d.setReponse(reponse);
        d.setDateTraitement(LocalDateTime.now());
        if (d.getTraitePar() == null) d.setTraitePar(perimetre.utilisateurCourant().getUsername());
    }

    private void evenement(Demande d, String libelle, String commentaire) {
        String auteur;
        try {
            auteur = nomComplet(perimetre.utilisateurCourant());
        } catch (Exception e) {
            auteur = "Système";
        }
        evenementRepository.save(DemandeEvenement.builder().demande(d).libelle(libelle).commentaire(commentaire)
                .auteur(auteur).date(LocalDateTime.now()).build());
    }

    private String prochainNumero() {
        String prefixe = "DEM-" + LocalDate.now().getYear() + "-";
        String dernier = demandeRepository.dernierNumero(prefixe);
        int suivant = dernier == null ? 1 : Integer.parseInt(dernier.substring(prefixe.length())) + 1;
        return prefixe + String.format("%04d", suivant);
    }

    private Demande trouverGerable(Long id) {
        Demande d = demandeRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Demande non trouvée : " + id));
        if (!perimetre.peutAcceder(d.getMembre().getId()))
            throw new AccessDeniedException("Cette demande ne fait pas partie de votre périmètre.");
        return d;
    }

    private Demande trouverOuverte(Long id) {
        Demande d = trouverGerable(id);
        if (estTraitee(d)) throw new ConflitException("Cette demande a déjà été traitée.");
        return d;
    }

    private Demande maDemandeEntite(Long id) {
        return demandeRepository.findByIdAndMembreId(id, perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Demande non trouvée : " + id));
    }

    private static boolean estTraitee(Demande d) {
        return d.getStatut() == StatutDemande.ACCEPTEE || d.getStatut() == StatutDemande.REJETEE;
    }

    private static String nomComplet(User u) {
        String n = ((u.getPrenom() == null ? "" : u.getPrenom()) + " " + (u.getNom() == null ? "" : u.getNom())).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private List<Modification> modifications(Demande d) {
        if (!StringUtils.hasText(d.getModifications())) return List.of();
        try {
            return objectMapper.readValue(d.getModifications(), new TypeReference<List<Modification>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Resume toResume(Demande d) {
        return Resume.builder()
                .id(d.getId()).numeroSuivi(d.getNumeroSuivi()).type(d.getType()).objet(d.getObjet()).statut(d.getStatut())
                .membreId(d.getMembre().getId()).membre(d.getMembre().getPrenom() + " " + d.getMembre().getNom())
                .identifiantSnj(d.getMembre().getIdentifiantSnj())
                .dateCreation(d.getDateCreation()).dateTraitement(d.getDateTraitement()).reponse(d.getReponse())
                .etape(estTraitee(d) ? 2 : d.getStatut() == StatutDemande.EN_ATTENTE ? 0 : 1)
                .build();
    }

    private Detail toDetail(Demande d) {
        Document p = d.getPieceJointe();
        return Detail.builder()
                .resume(toResume(d)).description(d.getDescription()).cohorte(d.getMembre().getCohorte().getNom())
                .telephone(d.getMembre().getNumeroTelephone()).modifications(modifications(d))
                .pieceJointeId(p == null ? null : p.getId()).pieceJointeNom(p == null ? null : p.getNomFichier())
                .pieceJointeTaille(p == null ? null : p.getTaille())
                .motifRejet(d.getMotifRejet())
                .elementsDemandes(StringUtils.hasText(d.getElementsDemandes()) ? List.of(d.getElementsDemandes().split("\n")) : List.of())
                .dateLimiteComplement(d.getDateLimiteComplement()).traitePar(d.getTraitePar())
                .delaiEstime(estTraitee(d) ? null : "Sous 48 h ouvrées")
                .suivi(evenementRepository.findByDemandeIdOrderByDateAsc(d.getId()).stream()
                        .map(e -> Evenement.builder().libelle(e.getLibelle()).commentaire(e.getCommentaire())
                                .auteur(e.getAuteur()).date(e.getDate()).build())
                        .toList())
                .build();
    }
}
