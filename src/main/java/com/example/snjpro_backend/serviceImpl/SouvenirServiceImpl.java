package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.SouvenirDTOs.*;
import com.example.snjpro_backend.entity.Cohorte;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.Souvenir;
import com.example.snjpro_backend.entity.SouvenirMedia;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.Enumerations.StatutSouvenir;
import com.example.snjpro_backend.enumeration.Enumerations.TypeMedia;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.repository.SouvenirMediaRepository;
import com.example.snjpro_backend.repository.SouvenirRepository;
import com.example.snjpro_backend.repository.UserRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import com.example.snjpro_backend.serviceInterface.SouvenirServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Souvenirs de formation (photos, vidéos, sons).
 * <ul>
 *   <li>Périmètre : administrateurs et agents → toutes les cohortes ; direction → lecture des souvenirs publiés ;
 *       responsable régional → cohortes de sa région ; responsable de cohorte → sa cohorte.</li>
 *   <li>Publié par le personnel : en ligne immédiatement. Envoyé par un membre : EN_ATTENTE jusqu'à validation.</li>
 *   <li>Un membre voit les souvenirs publiés de sa cohorte et ceux marqués « visibles par tous ».</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SouvenirServiceImpl implements SouvenirServiceInterface {
    private static final String ENTITE = "SOUVENIR";
    private static final String DOSSIER = "souvenirs";
    private static final int MAX_MEDIAS = 10;
    private static final Set<String> PHOTOS = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic");
    private static final Set<String> VIDEOS = Set.of("mp4", "m4v", "webm", "mov", "3gp");
    private static final Set<String> AUDIOS = Set.of("mp3", "wav", "ogg", "oga", "m4a", "aac", "opus");
    private static final Set<String> EXTENSIONS;
    static {
        Set<String> s = new HashSet<>(PHOTOS);
        s.addAll(VIDEOS);
        s.addAll(AUDIOS);
        EXTENSIONS = Set.copyOf(s);
    }
    private static final EnumSet<RoleEnum> MODERATEURS = EnumSet.of(RoleEnum.ROLE_ADMIN_SYSTEME, RoleEnum.ROLE_ADMIN_SNJ,
            RoleEnum.ROLE_RESPONSABLE_REGIONAL, RoleEnum.ROLE_RESPONSABLE_COHORTE);

    private final SouvenirRepository souvenirRepository;
    private final SouvenirMediaRepository mediaRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreRepository membreRepository;
    private final UserRepository userRepository;
    private final StockageFichierService stockage;
    private final PerimetreService perimetre;
    private final NotificationServiceInterface notificationService;
    private final HistoriqueServiceInterface historiqueService;

    // =====================================================================
    // Gestion (web)
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public Page<Resume> lister(Long cohorteId, StatutSouvenir statut, TypeMedia type, String recherche, Pageable pageable) {
        User u = perimetre.utilisateurCourant();
        StatutSouvenir effectif = u.getRole() == RoleEnum.ROLE_DIRECTION ? StatutSouvenir.PUBLIE : statut;
        Pageable tri = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "dateCreation"));
        return souvenirRepository.findAll(filtre(u, cohorteId, effectif, type, recherche), tri).map(this::toResume);
    }

    @Override @Transactional(readOnly = true)
    public Statistiques statistiques(Long cohorteId) {
        User u = perimetre.utilisateurCourant();
        List<Souvenir> tous = souvenirRepository.findAll(filtre(u, cohorteId, null, null, null));
        Map<TypeMedia, Long> parType = new EnumMap<>(TypeMedia.class);
        tous.stream().filter(s -> s.getStatut() == StatutSouvenir.PUBLIE)
                .flatMap(s -> s.getMedias().stream())
                .forEach(m -> parType.merge(m.getType(), 1L, Long::sum));
        return Statistiques.builder()
                .publies(tous.stream().filter(s -> s.getStatut() == StatutSouvenir.PUBLIE).count())
                .enAttente(u.getRole() == RoleEnum.ROLE_DIRECTION ? 0 : tous.stream().filter(s -> s.getStatut() == StatutSouvenir.EN_ATTENTE).count())
                .rejetes(u.getRole() == RoleEnum.ROLE_DIRECTION ? 0 : tous.stream().filter(s -> s.getStatut() == StatutSouvenir.REJETE).count())
                .photos(parType.getOrDefault(TypeMedia.PHOTO, 0L))
                .videos(parType.getOrDefault(TypeMedia.VIDEO, 0L))
                .audios(parType.getOrDefault(TypeMedia.AUDIO, 0L))
                .build();
    }

    @Override @Transactional(readOnly = true)
    public Resume obtenir(Long id) {
        return toResume(trouverVisible(id));
    }

    @Override
    public Resume creer(Edition dto, List<MultipartFile> fichiers) {
        User u = moderateur();
        if (dto.getCohorteId() == null) throw new IllegalArgumentException("Choisissez la cohorte du souvenir.");
        Cohorte c = cohorteRepository.findById(dto.getCohorteId())
                .orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + dto.getCohorteId()));
        verifierCohorte(u, c);
        Souvenir s = Souvenir.builder().cohorte(c).statut(StatutSouvenir.PUBLIE)
                .auteurUsername(u.getUsername()).auteurNom(nom(u))
                .moderePar(u.getUsername()).dateModeration(LocalDateTime.now()).build();
        appliquer(s, dto);
        ajouterFichiers(s, fichiers, true);
        s = souvenirRepository.save(s);
        historiqueService.enregistrer("CREATION", ENTITE, s.getId(), s.getTitre() + " · " + s.getMedias().size() + " fichier(s)");
        return toResume(s);
    }

    @Override
    public Resume modifier(Long id, Edition dto) {
        Souvenir s = trouverGerable(id);
        if (dto.getCohorteId() != null && !dto.getCohorteId().equals(s.getCohorte().getId())) {
            Cohorte c = cohorteRepository.findById(dto.getCohorteId())
                    .orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + dto.getCohorteId()));
            verifierCohorte(perimetre.utilisateurCourant(), c);
            s.setCohorte(c);
        }
        appliquer(s, dto);
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, s.getTitre());
        return toResume(souvenirRepository.save(s));
    }

    @Override
    public Resume ajouterMedias(Long id, List<MultipartFile> fichiers) {
        Souvenir s = trouverGerable(id);
        ajouterFichiers(s, fichiers, true);
        return toResume(souvenirRepository.save(s));
    }

    @Override
    public Resume supprimerMedia(Long id, Long mediaId) {
        Souvenir s = trouverGerable(id);
        SouvenirMedia m = s.getMedias().stream().filter(x -> x.getId().equals(mediaId)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Fichier non trouvé : " + mediaId));
        if (s.getMedias().size() == 1)
            throw new ConflitException("Un souvenir doit garder au moins un fichier : supprimez plutôt le souvenir.");
        s.getMedias().remove(m);
        stockage.supprimer(m.getChemin());
        return toResume(souvenirRepository.save(s));
    }

    @Override
    public Resume valider(Long id) {
        Souvenir s = trouverGerable(id);
        User u = perimetre.utilisateurCourant();
        s.setStatut(StatutSouvenir.PUBLIE);
        s.setMotifRejet(null);
        s.setModerePar(u.getUsername());
        s.setDateModeration(LocalDateTime.now());
        historiqueService.enregistrer("VALIDATION", ENTITE, id, s.getTitre());
        prevenirAuteur(s, "Votre souvenir est publié", "« " + s.getTitre() + " » est maintenant visible dans les souvenirs de la cohorte.");
        return toResume(souvenirRepository.save(s));
    }

    @Override
    public Resume rejeter(Long id, String motif) {
        Souvenir s = trouverGerable(id);
        User u = perimetre.utilisateurCourant();
        s.setStatut(StatutSouvenir.REJETE);
        s.setMotifRejet(motif);
        s.setModerePar(u.getUsername());
        s.setDateModeration(LocalDateTime.now());
        historiqueService.enregistrer("REJET", ENTITE, id, s.getTitre() + " · " + motif);
        prevenirAuteur(s, "Votre souvenir n'a pas été publié", "« " + s.getTitre() + " » : " + motif);
        return toResume(souvenirRepository.save(s));
    }

    @Override
    public void supprimer(Long id) {
        Souvenir s = trouverGerable(id);
        s.getMedias().forEach(m -> stockage.supprimer(m.getChemin()));
        souvenirRepository.delete(s);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, s.getTitre());
    }

    @Override @Transactional(readOnly = true)
    public Fichier fichier(Long mediaId) {
        SouvenirMedia m = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Fichier non trouvé : " + mediaId));
        verifierLecture(m.getSouvenir());
        return new Fichier(stockage.lire(m.getChemin()), m.getNomFichier(), m.getTypeMime());
    }

    // =====================================================================
    // Espace membre (application mobile)
    // =====================================================================

    @Override @Transactional(readOnly = true)
    public List<Resume> filMembre() {
        Membre m = membreCourant();
        Long cohorteId = m.getCohorte().getId();
        Specification<Souvenir> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("statut"), StatutSouvenir.PUBLIE),
                cb.or(cb.equal(root.get("cohorte").get("id"), cohorteId), cb.isTrue(root.get("visibleTous"))));
        return souvenirRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "dateCreation")).stream().map(this::toResume).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<Resume> mesEnvois() {
        return souvenirRepository.findByAuteurMembreIdOrderByDateCreationDesc(membreCourant().getId()).stream().map(this::toResume).toList();
    }

    @Override
    public Resume soumettre(Edition dto, List<MultipartFile> fichiers) {
        Membre m = membreCourant();
        User u = perimetre.utilisateurCourant();
        Souvenir s = Souvenir.builder().cohorte(m.getCohorte()).statut(StatutSouvenir.EN_ATTENTE)
                .auteurUsername(u.getUsername()).auteurNom(m.getPrenom() + " " + m.getNom()).auteurMembre(m).build();
        appliquer(s, dto);
        ajouterFichiers(s, fichiers, true);
        s = souvenirRepository.save(s);
        historiqueService.enregistrer("ENVOI", ENTITE, s.getId(), m.getIdentifiantSnj() + " · " + s.getTitre());
        alerterModerateurs(s);
        return toResume(s);
    }

    @Override
    public void supprimerMonEnvoi(Long id) {
        Souvenir s = souvenirRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Souvenir non trouvé : " + id));
        Membre m = membreCourant();
        if (s.getAuteurMembre() == null || !s.getAuteurMembre().getId().equals(m.getId()))
            throw new AccessDeniedException("Vous ne pouvez supprimer que vos propres envois.");
        s.getMedias().forEach(x -> stockage.supprimer(x.getChemin()));
        souvenirRepository.delete(s);
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    /** Filtre de liste : périmètre + critères. */
    private Specification<Souvenir> filtre(User u, Long cohorteId, StatutSouvenir statut, TypeMedia type, String recherche) {
        Set<Long> autorisees = cohortesAutorisees(u);
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (autorisees != null) p.add(autorisees.isEmpty() ? cb.disjunction() : root.get("cohorte").get("id").in(autorisees));
            if (cohorteId != null) p.add(cb.equal(root.get("cohorte").get("id"), cohorteId));
            if (statut != null) p.add(cb.equal(root.get("statut"), statut));
            if (type != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                var media = sq.from(SouvenirMedia.class);
                sq.select(media.get("id")).where(cb.equal(media.get("souvenir"), root), cb.equal(media.get("type"), type));
                p.add(cb.exists(sq));
            }
            if (StringUtils.hasText(recherche)) {
                String motif = "%" + recherche.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("titre")), motif), cb.like(cb.lower(root.get("legende")), motif),
                        cb.like(cb.lower(root.get("auteurNom")), motif), cb.like(cb.lower(root.get("lieu")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** null = toutes les cohortes ; sinon l'ensemble des cohortes du périmètre. */
    private Set<Long> cohortesAutorisees(User u) {
        return switch (u.getRole()) {
            case ROLE_ADMIN_SYSTEME, ROLE_ADMIN_SNJ, ROLE_DIRECTION -> null;
            case ROLE_RESPONSABLE_COHORTE -> u.getCohorteId() == null ? Set.of() : Set.of(u.getCohorteId());
            case ROLE_RESPONSABLE_REGIONAL -> {
                if (u.getRegion() == null) yield Set.of();
                Set<Long> ids = new HashSet<>();
                cohorteRepository.findAll().stream().filter(c -> u.getRegion().equalsIgnoreCase(c.getRegion())).forEach(c -> ids.add(c.getId()));
                yield ids;
            }
            default -> throw new AccessDeniedException("Accès aux souvenirs non autorisé.");
        };
    }

    private void verifierCohorte(User u, Cohorte c) {
        Set<Long> autorisees = cohortesAutorisees(u);
        if (autorisees != null && !autorisees.contains(c.getId()))
            throw new AccessDeniedException("Cette cohorte ne fait pas partie de votre périmètre.");
    }

    private User moderateur() {
        User u = perimetre.utilisateurCourant();
        if (!MODERATEURS.contains(u.getRole())) throw new AccessDeniedException("Action réservée aux gestionnaires.");
        return u;
    }

    private Souvenir trouverVisible(Long id) {
        Souvenir s = souvenirRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Souvenir non trouvé : " + id));
        User u = perimetre.utilisateurCourant();
        verifierCohorte(u, s.getCohorte());
        if (u.getRole() == RoleEnum.ROLE_DIRECTION && s.getStatut() != StatutSouvenir.PUBLIE)
            throw new AccessDeniedException("Ce souvenir n'est pas encore publié.");
        return s;
    }

    private Souvenir trouverGerable(Long id) {
        moderateur();
        return trouverVisible(id);
    }

    /** Lecture d'un fichier : gestionnaire dans son périmètre, ou membre autorisé à voir le souvenir. */
    private void verifierLecture(Souvenir s) {
        User u = perimetre.utilisateurCourant();
        if (u.getRole() != RoleEnum.ROLE_MEMBRE) {
            trouverVisible(s.getId());
            return;
        }
        Membre m = membreCourant();
        boolean auteur = s.getAuteurMembre() != null && s.getAuteurMembre().getId().equals(m.getId());
        boolean visible = s.getStatut() == StatutSouvenir.PUBLIE
                && (s.isVisibleTous() || s.getCohorte().getId().equals(m.getCohorte().getId()));
        if (!auteur && !visible) throw new AccessDeniedException("Ce souvenir ne vous est pas accessible.");
    }

    private Membre membreCourant() {
        Long id = perimetre.membreIdCourant();
        return membreRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + id));
    }

    private void appliquer(Souvenir s, Edition dto) {
        s.setTitre(dto.getTitre().trim());
        s.setLegende(StringUtils.hasText(dto.getLegende()) ? dto.getLegende().trim() : null);
        s.setDatePrise(dto.getDatePrise());
        s.setLieu(StringUtils.hasText(dto.getLieu()) ? dto.getLieu().trim() : null);
        s.setVisibleTous(dto.isVisibleTous());
    }

    private void ajouterFichiers(Souvenir s, List<MultipartFile> fichiers, boolean obligatoire) {
        List<MultipartFile> valides = fichiers == null ? List.of() : fichiers.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (obligatoire && valides.isEmpty() && s.getMedias().isEmpty())
            throw new IllegalArgumentException("Ajoutez au moins une photo, une vidéo ou un enregistrement audio.");
        if (s.getMedias().size() + valides.size() > MAX_MEDIAS)
            throw new IllegalArgumentException("Un souvenir contient au plus " + MAX_MEDIAS + " fichiers.");
        int ordre = s.getMedias().size();
        for (MultipartFile f : valides) {
            String ext = StockageFichierService.extension(f.getOriginalFilename());
            String chemin = stockage.enregistrer(f, DOSSIER, EXTENSIONS);
            s.getMedias().add(SouvenirMedia.builder().souvenir(s).type(typeDe(ext)).chemin(chemin)
                    .nomFichier(f.getOriginalFilename() != null ? f.getOriginalFilename() : "fichier." + ext)
                    .typeMime(StockageFichierService.typeMime(f.getOriginalFilename())).taille(f.getSize()).ordre(ordre++).build());
        }
    }

    private static TypeMedia typeDe(String extension) {
        if (VIDEOS.contains(extension)) return TypeMedia.VIDEO;
        if (AUDIOS.contains(extension)) return TypeMedia.AUDIO;
        return TypeMedia.PHOTO;
    }

    /** Alerte les administrateurs, agents SNJ et le(s) responsable(s) de la cohorte concernée. */
    private void alerterModerateurs(Souvenir s) {
        Long cohorteId = s.getCohorte().getId();
        String region = s.getCohorte().getRegion();
        userRepository.findAll().stream().filter(User::isActif).filter(u -> switch (u.getRole()) {
            case ROLE_ADMIN_SYSTEME, ROLE_ADMIN_SNJ -> true;
            case ROLE_RESPONSABLE_COHORTE -> cohorteId.equals(u.getCohorteId());
            case ROLE_RESPONSABLE_REGIONAL -> region != null && region.equalsIgnoreCase(u.getRegion());
            default -> false;
        }).forEach(u -> notificationService.alerterUtilisateur(u.getId(), "Nouveau souvenir à valider",
                s.getAuteurNom() + " · " + s.getTitre(), "SOUVENIR", "SOUVENIR", s.getId()));
    }

    /** Notification (application mobile) au membre auteur du souvenir, après modération. */
    private void prevenirAuteur(Souvenir s, String titre, String message) {
        if (s.getAuteurMembre() != null)
            notificationService.notifierMembres(List.of(s.getAuteurMembre()), TypeNotification.INFORMATION, titre, message, false, "SOUVENIR", s.getId());
    }

    private static String nom(User u) {
        String n = ((u.getPrenom() == null ? "" : u.getPrenom()) + " " + (u.getNom() == null ? "" : u.getNom())).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private Resume toResume(Souvenir s) {
        List<Media> medias = s.getMedias().stream().map(m -> Media.builder().id(m.getId()).type(m.getType())
                .nomFichier(m.getNomFichier()).typeMime(m.getTypeMime()).taille(m.getTaille()).build()).toList();
        return Resume.builder()
                .id(s.getId()).titre(s.getTitre()).legende(s.getLegende())
                .cohorteId(s.getCohorte().getId()).cohorteNom(s.getCohorte().getNom())
                .datePrise(s.getDatePrise()).lieu(s.getLieu()).statut(s.getStatut()).visibleTous(s.isVisibleTous())
                .auteurNom(s.getAuteurNom())
                .auteurMembreId(s.getAuteurMembre() != null ? s.getAuteurMembre().getId() : null)
                .auteurIdentifiantSnj(s.getAuteurMembre() != null ? s.getAuteurMembre().getIdentifiantSnj() : null)
                .envoyeParMembre(s.getAuteurMembre() != null)
                .motifRejet(s.getMotifRejet()).moderePar(s.getModerePar()).dateModeration(s.getDateModeration())
                .dateCreation(s.getDateCreation()).medias(medias)
                .nombrePhotos((int) medias.stream().filter(m -> m.getType() == TypeMedia.PHOTO).count())
                .nombreVideos((int) medias.stream().filter(m -> m.getType() == TypeMedia.VIDEO).count())
                .nombreAudios((int) medias.stream().filter(m -> m.getType() == TypeMedia.AUDIO).count())
                .build();
    }
}
