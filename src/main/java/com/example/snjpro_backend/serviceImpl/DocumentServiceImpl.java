package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.DocumentDTOs.*;
import com.example.snjpro_backend.entity.Document;
import com.example.snjpro_backend.entity.Dossier;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.DocumentRepository;
import com.example.snjpro_backend.repository.DossierRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.DocumentServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentServiceImpl implements DocumentServiceInterface {
    private static final String ENTITE = "DOCUMENT";
    private static final List<TypeDocument> PIECES_OBLIGATOIRES = List.of(TypeDocument.IDENTITE, TypeDocument.PHOTO);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentRepository documentRepository;
    private final DossierRepository dossierRepository;
    private final MembreRepository membreRepository;
    private final StockageFichierService stockage;
    private final GenerateurFichiers generateur;
    private final NotificationServiceInterface notificationService;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Bibliothèque de documents du SNJ
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<DossierDTO> dossiers() {
        return dossierRepository.findAll().stream()
                .map(d -> DossierDTO.builder().id(d.getId()).nom(d.getNom()).visibiliteDefaut(d.getVisibiliteDefaut())
                        .nombreDocuments(documentRepository.countByDossierId(d.getId())).build())
                .sorted((a, b) -> a.getNom().compareToIgnoreCase(b.getNom()))
                .toList();
    }

    @Override
    public DossierDTO creerDossier(DossierEdition dto) {
        if (dossierRepository.existsByNomIgnoreCase(dto.getNom().trim()))
            throw new ConflitException("Un dossier « " + dto.getNom().trim() + " » existe déjà.");
        Dossier d = dossierRepository.save(Dossier.builder().nom(dto.getNom().trim()).visibiliteDefaut(dto.getVisibiliteDefaut()).build());
        historiqueService.enregistrer("CREATION", "DOSSIER", d.getId(), d.getNom());
        return DossierDTO.builder().id(d.getId()).nom(d.getNom()).visibiliteDefaut(d.getVisibiliteDefaut()).build();
    }

    @Override
    public DossierDTO modifierDossier(Long id, DossierEdition dto) {
        Dossier d = dossierRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Dossier non trouvé : " + id));
        if (!d.getNom().equalsIgnoreCase(dto.getNom().trim()) && dossierRepository.existsByNomIgnoreCase(dto.getNom().trim()))
            throw new ConflitException("Un dossier « " + dto.getNom().trim() + " » existe déjà.");
        d.setNom(dto.getNom().trim());
        d.setVisibiliteDefaut(dto.getVisibiliteDefaut());
        dossierRepository.save(d);
        return DossierDTO.builder().id(d.getId()).nom(d.getNom()).visibiliteDefaut(d.getVisibiliteDefaut())
                .nombreDocuments(documentRepository.countByDossierId(id)).build();
    }

    @Override
    public void supprimerDossier(Long id) {
        Dossier d = dossierRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Dossier non trouvé : " + id));
        if (documentRepository.countByDossierId(id) > 0)
            throw new ConflitException("Le dossier contient des documents : déplacez-les ou supprimez-les d'abord.");
        dossierRepository.delete(d);
        historiqueService.enregistrer("SUPPRESSION", "DOSSIER", id, d.getNom());
    }

    @Override @Transactional(readOnly = true)
    public List<DocumentDTO> lister(Long dossierId, VisibiliteDocument visibilite, String recherche) {
        return documentRepository.findByMembreIsNullOrderByDateAjoutDesc().stream()
                .filter(d -> dossierId == null || (d.getDossier() != null && d.getDossier().getId().equals(dossierId)))
                .filter(d -> visibilite == null || d.getVisibilite() == visibilite)
                .filter(d -> !StringUtils.hasText(recherche) || d.getTitre().toLowerCase().contains(recherche.trim().toLowerCase()))
                .map(this::toDTO)
                .toList();
    }

    @Override
    public DocumentDTO televerser(MultipartFile fichier, String titre, Long dossierId, VisibiliteDocument visibilite) {
        Dossier dossier = dossierId == null ? null
                : dossierRepository.findById(dossierId).orElseThrow(() -> new EntityNotFoundException("Dossier non trouvé : " + dossierId));
        Document d = nouveau(fichier, titre, "documents");
        d.setDossier(dossier);
        d.setVisibilite(visibilite != null ? visibilite : dossier != null ? dossier.getVisibiliteDefaut() : VisibiliteDocument.MEMBRES);
        d = documentRepository.save(d);
        historiqueService.enregistrer("TELEVERSEMENT", ENTITE, d.getId(), d.getTitre());
        return toDTO(d);
    }

    @Override
    public DocumentDTO modifier(Long id, DocumentEdition dto) {
        Document d = trouver(id);
        d.setTitre(dto.getTitre().trim());
        if (d.getMembre() == null) {
            d.setDossier(dto.getDossierId() == null ? null : dossierRepository.findById(dto.getDossierId())
                    .orElseThrow(() -> new EntityNotFoundException("Dossier non trouvé : " + dto.getDossierId())));
            if (dto.getVisibilite() != null) d.setVisibilite(dto.getVisibilite());
        }
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, d.getTitre());
        return toDTO(documentRepository.save(d));
    }

    @Override
    public void supprimer(Long id) {
        Document d = trouver(id);
        documentRepository.delete(d);
        stockage.supprimer(d.getChemin());
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, d.getTitre());
    }

    @Override @Transactional(readOnly = true)
    public Fichier telecharger(Long id) {
        Document d = trouver(id);
        if (d.getMembre() != null && !perimetre.peutAcceder(d.getMembre().getId()))
            throw new AccessDeniedException("Ce document ne fait pas partie de votre périmètre.");
        return new Fichier(stockage.lire(d.getChemin()), d.getNomFichier(), d.getTypeMime());
    }

    @Override @Transactional(readOnly = true)
    public Espace espace() {
        long utilise = stockage.espaceUtilise();
        return Espace.builder().utiliseOctets(utilise).quotaOctets(stockage.quota())
                .pourcentage(stockage.quota() == 0 ? 0 : (int) Math.round(utilise * 100.0 / stockage.quota())).build();
    }

    // =====================================================================
    // Documents d'un membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public DocumentsMembre documentsMembre(Long membreId) {
        trouverMembre(membreId);
        List<Document> docs = documentRepository.findByMembreIdOrderByDateAjoutDesc(membreId);
        List<TypeDocument> manquantes = PIECES_OBLIGATOIRES.stream()
                .filter(t -> docs.stream().noneMatch(d -> d.getTypeDocument() == t)).toList();
        return DocumentsMembre.builder().documents(docs.stream().map(this::toDTO).toList()).piecesManquantes(manquantes).build();
    }

    @Override
    public DocumentDTO televerserPourMembre(Long membreId, MultipartFile fichier, TypeDocument type, String titre) {
        Membre m = trouverMembre(membreId);
        Document d = enregistrerPieceMembre(m, fichier, type, titre);
        d.setVerifie(true); // déposé par un agent : considéré comme contrôlé
        return toDTO(documentRepository.save(d));
    }

    @Override
    public DocumentDTO verifier(Long documentId, boolean verifie) {
        Document d = trouver(documentId);
        d.setVerifie(verifie);
        historiqueService.enregistrer(verifie ? "VERIFICATION" : "INVALIDATION", ENTITE, documentId, d.getTitre());
        return toDTO(documentRepository.save(d));
    }

    @Override
    public DocumentDTO genererAttestation(Long membreId) {
        Membre m = trouverMembre(membreId);
        List<GenerateurFichiers.Section> sections = new ArrayList<>();
        List<String[]> introduction = new ArrayList<>();
        introduction.add(new String[]{"Le Service National des Jeunes du Mali atteste que " + m.getPrenom() + " "
                + m.getNom().toUpperCase() + ", identifiant " + m.getIdentifiantSnj() + ", a accompli son service au sein du SNJ."});
        sections.add(new GenerateurFichiers.Section(null, introduction));
        sections.add(new GenerateurFichiers.Section("Informations", List.of(
                new String[]{"Né(e) le", m.getDateNaissance() != null ? m.getDateNaissance().format(DATE) : null},
                new String[]{"À", m.getLieuNaissance()},
                new String[]{"Cohorte", m.getCohorte().getNom()},
                new String[]{"Incorporation", m.getDateIncorporation() != null ? m.getDateIncorporation().format(DATE)
                        : m.getCohorte().getDateDebut() != null ? m.getCohorte().getDateDebut().format(DATE) : null},
                new String[]{"Statut", m.getStatut().name()})));
        sections.add(new GenerateurFichiers.Section("Parcours au SNJ", m.getParcours().stream()
                .map(p -> new String[]{(p.getDateDebut() != null ? p.getDateDebut().getYear() + " — " : "") + p.getIntitule()}).toList()));
        byte[] pdf = generateur.document("Attestation de participation", m.getPrenom() + " " + m.getNom(), sections,
                "Attestation délivrée pour servir et valoir ce que de droit.");
        Document d = documentRepository.save(Document.builder()
                .titre("Attestation de participation").nomFichier("attestation_" + m.getIdentifiantSnj() + ".pdf")
                .chemin(stockage.ecrire(pdf, "membres/" + membreId, "pdf")).typeMime("application/pdf").taille(pdf.length)
                .membre(m).typeDocument(TypeDocument.ATTESTATION).visibilite(VisibiliteDocument.INTERNE).verifie(true)
                .ajoutePar(auteur()).build());
        notificationService.notifierMembres(List.of(m), TypeNotification.INFORMATION, "Attestation disponible",
                "Votre attestation de participation est disponible dans « Mes documents ».", false, "DOCUMENT", d.getId());
        historiqueService.enregistrer("ATTESTATION", "MEMBRE", membreId, m.getIdentifiantSnj());
        return toDTO(d);
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public MesDocuments mesDocuments() {
        Long membreId = perimetre.membreIdCourant();
        return MesDocuments.builder()
                .personnels(documentRepository.findByMembreIdOrderByDateAjoutDesc(membreId).stream().map(this::toDTO).toList())
                .duSnj(documentRepository.findByMembreIsNullAndVisibiliteInOrderByDateAjoutDesc(
                        List.of(VisibiliteDocument.MEMBRES, VisibiliteDocument.PUBLIC)).stream().map(this::toDTO).toList())
                .build();
    }

    @Override
    public DocumentDTO televerserMonDocument(MultipartFile fichier, TypeDocument type, String titre) {
        Membre m = trouverMembre(perimetre.membreIdCourant());
        return toDTO(enregistrerPieceMembre(m, fichier, type, titre));
    }

    @Override @Transactional(readOnly = true)
    public Fichier telechargerMonDocument(Long id) {
        Document d = trouver(id);
        boolean personnel = d.getMembre() != null && d.getMembre().getId().equals(perimetre.membreIdCourant());
        boolean partage = d.getMembre() == null && d.getVisibilite() != VisibiliteDocument.INTERNE;
        if (!personnel && !partage) throw new EntityNotFoundException("Document non trouvé : " + id);
        return new Fichier(stockage.lire(d.getChemin()), d.getNomFichier(), d.getTypeMime());
    }

    @Override
    public Document enregistrerPieceMembre(Membre membre, MultipartFile fichier, TypeDocument type, String titre) {
        Document d = nouveau(fichier, StringUtils.hasText(titre) ? titre : libelle(type), "membres/" + membre.getId());
        d.setMembre(membre);
        d.setTypeDocument(type == null ? TypeDocument.AUTRE : type);
        d.setVisibilite(VisibiliteDocument.INTERNE);
        d = documentRepository.save(d);
        historiqueService.enregistrer("TELEVERSEMENT", "MEMBRE", membre.getId(), d.getTitre());
        return d;
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private Document nouveau(MultipartFile fichier, String titre, String sousDossier) {
        String nom = fichier.getOriginalFilename() == null ? "document" : fichier.getOriginalFilename();
        return Document.builder()
                .titre(StringUtils.hasText(titre) ? titre.trim() : nom)
                .nomFichier(nom)
                .chemin(stockage.enregistrer(fichier, sousDossier))
                .typeMime(StockageFichierService.typeMime(nom))
                .taille(fichier.getSize())
                .ajoutePar(auteur())
                .build();
    }

    private String auteur() {
        try {
            return perimetre.utilisateurCourant().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }

    private static String libelle(TypeDocument type) {
        if (type == null) return "Document";
        return switch (type) {
            case ATTESTATION -> "Attestation";
            case DIPLOME -> "Diplôme";
            case IDENTITE -> "Pièce d'identité";
            case PHOTO -> "Photo d'identité";
            case CARTE_MEMBRE -> "Carte membre";
            case CV -> "CV";
            case AUTRE -> "Document";
        };
    }

    private Membre trouverMembre(Long id) {
        return membreRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + id));
    }

    private Document trouver(Long id) {
        return documentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Document non trouvé : " + id));
    }

    private DocumentDTO toDTO(Document d) {
        boolean estMembre = false;
        try {
            estMembre = perimetre.utilisateurCourant().getRole() == RoleEnum.ROLE_MEMBRE;
        } catch (Exception ignored) {
            // appel hors requête authentifiée
        }
        return DocumentDTO.builder()
                .id(d.getId()).titre(d.getTitre()).nomFichier(d.getNomFichier())
                .extension(StockageFichierService.extension(d.getNomFichier())).typeMime(d.getTypeMime()).taille(d.getTaille())
                .dossierId(d.getDossier() != null ? d.getDossier().getId() : null)
                .dossier(d.getDossier() != null ? d.getDossier().getNom() : null)
                .visibilite(d.getVisibilite()).typeDocument(d.getTypeDocument()).verifie(d.isVerifie())
                .nouveau(d.getDateAjout() != null && d.getDateAjout().isAfter(LocalDateTime.now().minusDays(7)))
                .ajoutePar(d.getAjoutePar()).dateAjout(d.getDateAjout())
                .urlTelechargement(estMembre ? "/api/mon-espace/documents/" + d.getId() + "/telecharger"
                        : "/api/documents/" + d.getId() + "/telecharger")
                .build();
    }
}
