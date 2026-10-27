package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.ActualiteDTOs.*;
import com.example.snjpro_backend.entity.Actualite;
import com.example.snjpro_backend.entity.InscriptionActualite;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.Enumerations.CategorieActualite;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.ActualiteRepository;
import com.example.snjpro_backend.repository.InscriptionActualiteRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.ActualiteServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ActualiteServiceImpl implements ActualiteServiceInterface {
    private static final String ENTITE = "ACTUALITE";

    private final ActualiteRepository actualiteRepository;
    private final InscriptionActualiteRepository inscriptionRepository;
    private final MembreRepository membreRepository;
    private final SelectionMembres selection;
    private final NotificationServiceInterface notificationService;
    private final StockageFichierService stockage;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Gestion
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> lister(StatutActualite statut, CategorieActualite categorie, String recherche) {
        User u = perimetre.utilisateurCourant();
        return actualiteRepository.findAllByOrderByDateCreationDesc().stream()
                .filter(a -> gerable(a, u))
                .filter(a -> statut == null || a.getStatut() == statut)
                .filter(a -> categorie == null || a.getCategorie() == categorie)
                .filter(a -> !StringUtils.hasText(recherche) || a.getTitre().toLowerCase().contains(recherche.trim().toLowerCase()))
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Compteurs compteurs() {
        User u = perimetre.utilisateurCourant();
        List<Actualite> l = actualiteRepository.findAllByOrderByDateCreationDesc().stream().filter(a -> gerable(a, u)).toList();
        java.util.function.Function<StatutActualite, Long> nb = s -> l.stream().filter(a -> a.getStatut() == s).count();
        return Compteurs.builder()
                .toutes(l.size())
                .publiees(nb.apply(StatutActualite.PUBLIEE))
                .brouillons(nb.apply(StatutActualite.BROUILLON))
                .programmees(nb.apply(StatutActualite.PROGRAMMEE))
                .archivees(nb.apply(StatutActualite.ARCHIVEE))
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Detail obtenir(Long id) { return toDetail(trouverGerable(id), null); }

    @Override
    public Detail creer(Edition dto) {
        Actualite a = new Actualite();
        a.setAuteur(perimetre.utilisateurCourant().getUsername());
        appliquer(a, dto);
        a = actualiteRepository.save(a);
        historiqueService.enregistrer("CREATION", ENTITE, a.getId(), a.getTitre());
        if (dto.getStatut() == StatutActualite.PUBLIEE) return publier(a.getId());
        return toDetail(a, null);
    }

    @Override
    public Detail modifier(Long id, Edition dto) {
        Actualite a = trouverGerable(id);
        boolean publiee = a.getStatut() == StatutActualite.PUBLIEE;
        appliquer(a, dto);
        if (publiee) a.setStatut(StatutActualite.PUBLIEE);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, a.getTitre());
        a = actualiteRepository.save(a);
        if (!publiee && dto.getStatut() == StatutActualite.PUBLIEE) return publier(id);
        return toDetail(a, null);
    }

    @Override
    public Detail publier(Long id) {
        Actualite a = trouverGerable(id);
        if (a.getStatut() == StatutActualite.PUBLIEE) throw new ConflitException("Cette actualité est déjà publiée.");
        diffuser(a);
        historiqueService.enregistrer("PUBLICATION", ENTITE, id, a.getTitre());
        return toDetail(actualiteRepository.save(a), null);
    }

    @Override
    public Detail archiver(Long id) {
        Actualite a = trouverGerable(id);
        a.setStatut(StatutActualite.ARCHIVEE);
        historiqueService.enregistrer("ARCHIVAGE", ENTITE, id, a.getTitre());
        return toDetail(actualiteRepository.save(a), null);
    }

    @Override
    public void supprimer(Long id) {
        Actualite a = trouverGerable(id);
        if (a.getStatut() == StatutActualite.PUBLIEE)
            throw new ConflitException("Archivez d'abord l'actualité publiée avant de la supprimer.");
        inscriptionRepository.deleteAll(inscriptionRepository.findByActualiteIdOrderByDateInscriptionDesc(id));
        stockage.supprimer(a.getImageChemin());
        actualiteRepository.delete(a);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, a.getTitre());
    }

    @Override
    public Detail changerImage(Long id, MultipartFile image) {
        Actualite a = trouverGerable(id);
        if (!StockageFichierService.typeMime(image.getOriginalFilename()).startsWith("image/"))
            throw new IllegalArgumentException("L'image de couverture doit être au format PNG, JPG, GIF ou WEBP.");
        String ancienne = a.getImageChemin();
        a.setImageChemin(stockage.enregistrer(image, "actualites"));
        stockage.supprimer(ancienne);
        return toDetail(actualiteRepository.save(a), null);
    }

    @Override @Transactional(readOnly = true)
    public byte[] image(Long id) { return stockage.lire(trouver(id).getImageChemin()); }

    @Override @Transactional(readOnly = true)
    public List<Inscrit> inscrits(Long id) {
        trouverGerable(id);
        return inscriptionRepository.findByActualiteIdOrderByDateInscriptionDesc(id).stream()
                .map(i -> Inscrit.builder()
                        .membreId(i.getMembre().getId()).identifiantSnj(i.getMembre().getIdentifiantSnj())
                        .prenom(i.getMembre().getPrenom()).nom(i.getMembre().getNom())
                        .telephone(i.getMembre().getNumeroTelephone()).dateInscription(i.getDateInscription())
                        .build())
                .toList();
    }

    /** Publication des actualités programmées et archivage des actualités expirées. */
    @Scheduled(fixedDelay = 60_000)
    public void traitementsAutomatiques() {
        for (Actualite a : actualiteRepository.findByStatutAndDatePublicationLessThanEqual(StatutActualite.PROGRAMMEE, LocalDateTime.now())) {
            diffuser(a);
            actualiteRepository.save(a);
        }
        actualiteRepository.findByStatutOrderByDatePublicationDesc(StatutActualite.PUBLIEE).stream()
                .filter(a -> a.getDateExpiration() != null && a.getDateExpiration().isBefore(LocalDate.now()))
                .forEach(a -> {
                    a.setStatut(StatutActualite.ARCHIVEE);
                    actualiteRepository.save(a);
                });
    }

    // =====================================================================
    // Espace membre
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> fil(CategorieActualite categorie) {
        Membre m = membreCourant();
        return actualiteRepository.findByStatutOrderByDatePublicationDesc(StatutActualite.PUBLIEE).stream()
                .filter(a -> categorie == null || a.getCategorie() == categorie)
                .filter(a -> visiblePour(a, m))
                .map(this::toResume)
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public Resume aLaUne() {
        Membre m = membreCourant();
        return actualiteRepository.findByStatutOrderByDatePublicationDesc(StatutActualite.PUBLIEE).stream()
                .filter(a -> visiblePour(a, m))
                .max(Comparator.comparing(Actualite::isEpingle).thenComparing(Actualite::getDatePublication))
                .map(this::toResume)
                .orElse(null);
    }

    @Override
    public Detail lire(Long id) {
        Membre m = membreCourant();
        Actualite a = trouverVisible(id, m);
        a.setVues(a.getVues() + 1);
        return toDetail(actualiteRepository.save(a), m);
    }

    @Override
    public Detail inscrire(Long id) {
        Membre m = membreCourant();
        Actualite a = trouverVisible(id, m);
        if (!a.isInscriptionsOuvertes()) throw new ConflitException("Les inscriptions ne sont pas ouvertes pour cette actualité.");
        if (inscriptionRepository.findByActualiteIdAndMembreId(id, m.getId()).isEmpty()) {
            inscriptionRepository.save(InscriptionActualite.builder().actualite(a).membre(m).dateInscription(LocalDateTime.now()).build());
            historiqueService.enregistrer("INSCRIPTION", ENTITE, id, m.getIdentifiantSnj());
        }
        return toDetail(a, m);
    }

    @Override
    public Detail desinscrire(Long id) {
        Membre m = membreCourant();
        Actualite a = trouverVisible(id, m);
        inscriptionRepository.findByActualiteIdAndMembreId(id, m.getId()).ifPresent(inscriptionRepository::delete);
        return toDetail(a, m);
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private void appliquer(Actualite a, Edition dto) {
        if (dto.getStatut() == StatutActualite.PROGRAMMEE
                && (dto.getDatePublication() == null || !dto.getDatePublication().isAfter(LocalDateTime.now())))
            throw new IllegalArgumentException("Une publication programmée doit avoir une date de publication future.");
        if (dto.getCible() != CibleNotification.TOUS && dto.getValeurs().isEmpty() && !perimetre.estResponsable(perimetre.utilisateurCourant()))
            throw new IllegalArgumentException("Précisez les régions ou cohortes visées.");
        a.setTitre(dto.getTitre().trim());
        a.setSousTitre(dto.getSousTitre());
        a.setContenu(dto.getContenu());
        a.setCategorie(dto.getCategorie());
        // PUBLIEE est appliqué ensuite par publier() (notification des membres) ; ARCHIVEE via archiver().
        if (dto.getStatut() == StatutActualite.PROGRAMMEE) {
            a.setStatut(StatutActualite.PROGRAMMEE);
            a.setDatePublication(dto.getDatePublication());
        } else {
            a.setStatut(StatutActualite.BROUILLON);
        }
        a.setDateExpiration(dto.getDateExpiration());
        // Un responsable publie uniquement pour sa région (régional) ou sa cohorte (cohorte)
        User u = perimetre.utilisateurCourant();
        switch (u.getRole()) {
            case ROLE_RESPONSABLE_REGIONAL -> { a.setCible(CibleNotification.REGION); a.setCibleValeurs(u.getRegion()); }
            case ROLE_RESPONSABLE_COHORTE -> { a.setCible(CibleNotification.COHORTE); a.setCibleValeurs(String.valueOf(u.getCohorteId())); }
            default -> { a.setCible(dto.getCible()); a.setCibleValeurs(MobilisationServiceImpl.joindre(dto.getValeurs())); }
        }
        a.setNotifierMembres(dto.isNotifierMembres());
        a.setEpingle(dto.isEpingle());
        a.setInscriptionsOuvertes(dto.isInscriptionsOuvertes());
    }

    private void diffuser(Actualite a) {
        a.setStatut(StatutActualite.PUBLIEE);
        a.setDatePublication(LocalDateTime.now());
        if (a.isNotifierMembres()) {
            notificationService.notifierMembres(selection.parCible(a.getCible(), MobilisationServiceImpl.separer(a.getCibleValeurs())),
                    TypeNotification.ACTUALITE, "Nouvelle actualité", a.getTitre(), false, "ACTUALITE", a.getId());
        }
    }

    private boolean visiblePour(Actualite a, Membre m) {
        List<String> v = MobilisationServiceImpl.separer(a.getCibleValeurs());
        return switch (a.getCible()) {
            case TOUS -> true;
            case REGION -> m.getLocalisation() != null && v.stream().anyMatch(r -> r.equalsIgnoreCase(m.getLocalisation().getRegion()));
            case COHORTE -> v.contains(String.valueOf(m.getCohorte().getId()));
            case SELECTION -> v.contains(String.valueOf(m.getId()));
        };
    }

    private Membre membreCourant() {
        return membreRepository.findById(perimetre.membreIdCourant())
                .orElseThrow(() -> new EntityNotFoundException("Fiche membre introuvable."));
    }

    private Actualite trouverVisible(Long id, Membre m) {
        Actualite a = trouver(id);
        if (a.getStatut() != StatutActualite.PUBLIEE || !visiblePour(a, m))
            throw new EntityNotFoundException("Actualité non trouvée : " + id);
        return a;
    }

    /** Administrateurs et direction gèrent toutes les actualités ; un responsable uniquement celles qu'il a rédigées. */
    private boolean gerable(Actualite a, User u) {
        return !perimetre.estResponsable(u) || u.getUsername().equals(a.getAuteur());
    }

    private Actualite trouverGerable(Long id) {
        Actualite a = trouver(id);
        if (!gerable(a, perimetre.utilisateurCourant()))
            throw new org.springframework.security.access.AccessDeniedException("Cette actualité ne fait pas partie de votre périmètre.");
        return a;
    }

    private Actualite trouver(Long id) {
        return actualiteRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Actualité non trouvée : " + id));
    }

    private Resume toResume(Actualite a) {
        String texte = a.getContenu() == null ? "" : a.getContenu().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        int mots = texte.isEmpty() ? 0 : texte.split(" ").length;
        return Resume.builder()
                .id(a.getId()).titre(a.getTitre()).sousTitre(a.getSousTitre())
                .extrait(texte.length() > 160 ? texte.substring(0, 157) + "…" : texte)
                .categorie(a.getCategorie()).statut(a.getStatut())
                .imageUrl(a.getImageChemin() != null ? "/api/actualites/" + a.getId() + "/image" : null)
                .auteur(a.getAuteur()).datePublication(a.getDatePublication()).dateModification(a.getDateModification())
                .vues(a.getVues()).epingle(a.isEpingle()).minutesLecture(Math.max(1, (int) Math.ceil(mots / 200.0)))
                .build();
    }

    private Detail toDetail(Actualite a, Membre membre) {
        String texte = a.getContenu() == null ? "" : a.getContenu().replaceAll("<[^>]+>", " ").trim();
        return Detail.builder()
                .resume(toResume(a)).contenu(a.getContenu()).dateExpiration(a.getDateExpiration())
                .cible(a.getCible()).valeurs(MobilisationServiceImpl.separer(a.getCibleValeurs()))
                .notifierMembres(a.isNotifierMembres()).inscriptionsOuvertes(a.isInscriptionsOuvertes())
                .nombreInscrits(a.getId() == null ? 0 : inscriptionRepository.countByActualiteId(a.getId()))
                .nombreMots(texte.isEmpty() ? 0 : texte.split("\\s+").length)
                .inscrit(membre == null ? null : inscriptionRepository.findByActualiteIdAndMembreId(a.getId(), membre.getId()).isPresent())
                .build();
    }
}
