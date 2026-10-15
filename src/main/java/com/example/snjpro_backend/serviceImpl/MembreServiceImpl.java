package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.entity.Cohorte;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.mapper.MembreMapper;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import com.example.snjpro_backend.specification.MembreSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MembreServiceImpl implements MembreServiceInterface {
    private static final String ENTITE = "MEMBRE";

    private final MembreRepository membreRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreCompetenceRepository competenceRepository;
    private final ParcoursSNJRepository parcoursRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembreMapper mapper;
    private final DoublonDetecteur doublonDetecteur;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;
    private final SuiviModifications suivi;
    private final StockageFichierService stockage;

    @Override
    public MembreResponseDTO ajouterMembre(MembreDTO dto, String createdBy, boolean forcer) {
        Cohorte cohorte = trouverCohorte(dto.getCohorteId());
        if (cohorte.getStatut() == StatutCohorteEnum.ARCHIVEE)
            throw new ConflitException("Impossible d'ajouter un membre à une cohorte archivée.");
        if (!forcer) {
            List<String> motifs = doublonDetecteur.motifs(dto.getPrenom(), dto.getNom(), dto.getDateNaissance(),
                    dto.getNumeroTelephone(), dto.getEmail(), null);
            if (!motifs.isEmpty())
                throw new ConflitException("Doublon potentiel : " + String.join(" ; ", motifs)
                        + ". Utilisez forcer=true pour enregistrer malgré tout.");
        }
        Membre membre = Membre.builder()
                .identifiantSnj(genererIdentifiantSnj(cohorte.getId()))
                .prenom(dto.getPrenom().trim())
                .nom(dto.getNom().trim())
                .sexe(dto.getSexe())
                .dateNaissance(dto.getDateNaissance())
                .lieuNaissance(dto.getLieuNaissance())
                .numeroTelephone(DoublonDetecteur.normaliserTelephone(dto.getNumeroTelephone()))
                .email(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim().toLowerCase() : null)
                .photoUrl(dto.getPhotoUrl())
                .statut(dto.getStatut() != null ? dto.getStatut() : StatutMembreEnum.ACTIF)
                .cohorte(cohorte)
                .createdBy(createdBy)
                .build();
        appliquerComplements(membre, dto);
        membre = membreRepository.save(membre);
        historiqueService.enregistrer("CREATION", ENTITE, membre.getId(), membre.getIdentifiantSnj());
        return mapper.toResponseDTO(membre);
    }

    @Override
    public MembreResponseDTO modifierMembre(Long id, MembreDTO dto) {
        Membre membre = trouver(id);
        java.util.Map<String, String> avant = suivi.instantane(membre);
        List<String> motifs = doublonDetecteur.motifs(null, null, null,
                dto.getNumeroTelephone(), dto.getEmail(), id);
        if (!motifs.isEmpty())
            throw new ConflitException(String.join(" ; ", motifs));
        membre.setPrenom(dto.getPrenom().trim());
        membre.setNom(dto.getNom().trim());
        membre.setSexe(dto.getSexe());
        membre.setDateNaissance(dto.getDateNaissance());
        membre.setLieuNaissance(dto.getLieuNaissance());
        membre.setNumeroTelephone(DoublonDetecteur.normaliserTelephone(dto.getNumeroTelephone()));
        membre.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim().toLowerCase() : null);
        membre.setPhotoUrl(dto.getPhotoUrl());
        if (dto.getStatut() != null) membre.setStatut(dto.getStatut());
        if (dto.getCohorteId() != null && !dto.getCohorteId().equals(membre.getCohorte().getId())) {
            membre.setCohorte(trouverCohorte(dto.getCohorteId()));
        }
        if (!Objects.equals(avant.get("numeroTelephone"), membre.getNumeroTelephone())) membre.setTelephoneVerifie(false);
        appliquerComplements(membre, dto);
        historiqueService.enregistrerModifications("MODIFICATION", ENTITE, id, "Membre · " + membre.getPrenom() + " " + membre.getNom(),
                suivi.differences(avant, suivi.instantane(membre)));
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override @Transactional(readOnly = true)
    public MembreResponseDTO obtenirMembre(Long id) {
        return mapper.toResponseDTO(trouver(id));
    }

    @Override @Transactional(readOnly = true)
    public MembreResponseDTO obtenirParIdentifiantSnj(String identifiantSnj) {
        return membreRepository.findByIdentifiantSnj(identifiantSnj).map(mapper::toResponseDTO)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + identifiantSnj));
    }

    @Override @Transactional(readOnly = true)
    public Page<MembreResponseDTO> obtenirTousLesMembres(Pageable pageable) {
        return rechercher(new MembreFilterDTO(), pageable);
    }

    @Override @Transactional(readOnly = true)
    public Page<MembreResponseDTO> rechercherMembres(String query, Pageable pageable) {
        MembreFilterDTO filter = new MembreFilterDTO();
        filter.setQuery(query);
        return rechercher(filter, pageable);
    }

    @Override @Transactional(readOnly = true)
    public Page<MembreResponseDTO> obtenirMembresParCohorte(Long cohorteId, Pageable pageable) {
        trouverCohorte(cohorteId);
        MembreFilterDTO filter = new MembreFilterDTO();
        filter.setCohorteId(cohorteId);
        perimetre.restreindre(filter);
        if (!cohorteId.equals(filter.getCohorteId()))
            throw new AccessDeniedException("Cette cohorte ne fait pas partie de votre périmètre.");
        return membreRepository.findAll(MembreSpecification.avecFiltres(filter), pageable).map(mapper::toResponseDTO);
    }

    @Override
    public MembreResponseDTO archiverMembre(Long id) {
        return changerStatut(id, StatutMembreEnum.ARCHIVE);
    }

    @Override
    public MembreResponseDTO changerStatut(Long id, StatutMembreEnum statut) {
        Membre membre = trouver(id);
        StatutMembreEnum ancien = membre.getStatut();
        membre.setStatut(statut);
        historiqueService.enregistrer("CHANGEMENT_STATUT", ENTITE, id, ancien + " -> " + statut);
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override
    public void supprimerMembre(Long id) {
        Membre membre = trouver(id);
        membreRepository.delete(membre);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, membre.getIdentifiantSnj() + " - "
                + membre.getPrenom() + " " + membre.getNom());
    }

    /**
     * Identifiant unique au format SNJ-{cohorte}-{séquence}. La séquence est incrémentée jusqu'à trouver
     * une valeur libre, ce qui garantit l'unicité même après suppression ou changement de cohorte.
     */
    @Override
    public String genererIdentifiantSnj(Long cohorteId) {
        long sequence = membreRepository.countByCohorteId(cohorteId) + 1;
        String identifiant;
        do {
            identifiant = String.format("SNJ-%d-%04d", cohorteId, sequence++);
        } while (membreRepository.existsByIdentifiantSnj(identifiant));
        return identifiant;
    }

    @Override @Transactional(readOnly = true)
    public ProfilMembreDTO obtenirProfilComplet(Long id) {
        Membre membre = trouver(id);
        List<CompetenceDTO> competences = competenceRepository.findByMembreId(id).stream()
                .map(mapper::toCompetenceDTO).toList();
        List<ParcoursSNJDTO> parcours = parcoursRepository.findByMembreIdOrderByDateDebutDesc(id).stream()
                .map(mapper::toParcoursDTO).toList();
        return ProfilMembreDTO.builder()
                .membre(mapper.toResponseDTO(membre))
                .competences(competences)
                .parcours(parcours)
                .tauxCompletude(tauxCompletude(membre, !competences.isEmpty(), !parcours.isEmpty()))
                .build();
    }

    @Override
    public MembreResponseDTO creerCompte(Long membreId, CompteMembreDTO dto) {
        Membre membre = trouver(membreId);
        if (membre.getUserId() != null && userRepository.existsById(membre.getUserId()))
            throw new ConflitException("Ce membre possède déjà un compte.");
        String username = StringUtils.hasText(dto.getUsername()) ? dto.getUsername().trim()
                : membre.getIdentifiantSnj().toLowerCase();
        String email = StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : membre.getEmail();
        if (!StringUtils.hasText(email))
            throw new IllegalArgumentException("Un email est nécessaire pour créer le compte (fiche membre ou requête).");
        if (userRepository.existsByUsername(username))
            throw new ConflitException("Le nom d'utilisateur '" + username + "' est déjà utilisé.");
        if (userRepository.existsByEmail(email))
            throw new ConflitException("L'email '" + email + "' est déjà utilisé par un autre compte.");
        User user = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(dto.getPassword()))
                .email(email)
                .prenom(membre.getPrenom())
                .nom(membre.getNom())
                .role(RoleEnum.ROLE_MEMBRE)
                .actif(true)
                .createdBy(historiqueUtilisateur())
                .build());
        membre.setUserId(user.getId());
        historiqueService.enregistrer("CREATION_COMPTE", ENTITE, membreId, "Compte : " + username);
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override
    public MembreResponseDTO mettreAJourProfil(Long membreId, MiseAJourProfilDTO dto) {
        Membre membre = trouver(membreId);
        List<String> motifs = doublonDetecteur.motifs(null, null, null,
                dto.getNumeroTelephone(), dto.getEmail(), membreId);
        if (!motifs.isEmpty())
            throw new ConflitException(String.join(" ; ", motifs));
        if (dto.getNumeroTelephone() != null)
            membre.setNumeroTelephone(DoublonDetecteur.normaliserTelephone(dto.getNumeroTelephone()));
        if (dto.getEmail() != null)
            membre.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim().toLowerCase() : null);
        if (dto.getPhotoUrl() != null)
            membre.setPhotoUrl(dto.getPhotoUrl());
        historiqueService.enregistrer("MISE_A_JOUR_PROFIL", ENTITE, membreId, "Par le membre");
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    private Page<MembreResponseDTO> rechercher(MembreFilterDTO filter, Pageable pageable) {
        perimetre.restreindre(filter);
        return membreRepository.findAll(MembreSpecification.avecFiltres(filter), pageable).map(mapper::toResponseDTO);
    }

    @Override
    public MembreResponseDTO restaurer(Long membreId, java.util.Map<String, String> valeurs, Long historiqueId) {
        Membre membre = trouver(membreId);
        java.util.Map<String, String> avant = suivi.instantane(membre);
        valeurs.forEach((champ, valeur) -> suivi.appliquer(membre, champ, valeur));
        historiqueService.enregistrerModifications("RESTAURATION", ENTITE, membreId,
                "Restauration de l'opération n°" + historiqueId, suivi.differences(avant, suivi.instantane(membre)));
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override @Transactional(readOnly = true)
    public List<String> verifierDoublons(String prenom, String nom, java.time.LocalDate dateNaissance, String telephone, String email, Long exclureId) {
        return doublonDetecteur.motifs(prenom, nom, dateNaissance, telephone, email, exclureId);
    }

    @Override @Transactional(readOnly = true)
    public String identifiantSuivant(Long cohorteId) {
        trouverCohorte(cohorteId);
        return genererIdentifiantSnj(cohorteId);
    }

    @Override
    public MembreResponseDTO changerDisponibilite(Long membreId, boolean disponible) {
        Membre membre = trouver(membreId);
        membre.setDisponibleMobilisation(disponible);
        historiqueService.enregistrer(disponible ? "DISPONIBLE_MOBILISATION" : "INDISPONIBLE_MOBILISATION", ENTITE, membreId, null);
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override
    public MembreResponseDTO changerPhoto(Long membreId, org.springframework.web.multipart.MultipartFile photo) {
        if (!StockageFichierService.typeMime(photo.getOriginalFilename()).startsWith("image/"))
            throw new IllegalArgumentException("La photo doit être une image (PNG, JPG, GIF ou WEBP).");
        Membre membre = trouver(membreId);
        String ancienne = membre.getPhotoUrl();
        membre.setPhotoUrl(stockage.enregistrer(photo, "photos/membres"));
        if (ancienne != null && !ancienne.startsWith("http")) stockage.supprimer(ancienne);
        historiqueService.enregistrer("PHOTO", ENTITE, membreId, membre.getIdentifiantSnj());
        return mapper.toResponseDTO(membreRepository.save(membre));
    }

    @Override @Transactional(readOnly = true)
    public com.example.snjpro_backend.dto.DocumentDTOs.Fichier photo(Long membreId) {
        Membre membre = trouver(membreId);
        if (!StringUtils.hasText(membre.getPhotoUrl()) || membre.getPhotoUrl().startsWith("http"))
            throw new EntityNotFoundException("Aucune photo pour ce membre.");
        return new com.example.snjpro_backend.dto.DocumentDTOs.Fichier(stockage.lire(membre.getPhotoUrl()),
                "photo-" + membre.getIdentifiantSnj(), StockageFichierService.typeMime(membre.getPhotoUrl()));
    }

    /** Champs complémentaires du formulaire « Nouveau membre » (renseignés seulement s'ils sont fournis). */
    private void appliquerComplements(Membre m, MembreDTO dto) {
        if (dto.getTelephoneSecondaire() != null) m.setTelephoneSecondaire(DoublonDetecteur.normaliserTelephone(dto.getTelephoneSecondaire()));
        if (dto.getPersonneAPrevenir() != null) m.setPersonneAPrevenir(dto.getPersonneAPrevenir());
        if (dto.getPersonneAPrevenirTelephone() != null)
            m.setPersonneAPrevenirTelephone(DoublonDetecteur.normaliserTelephone(dto.getPersonneAPrevenirTelephone()));
        if (dto.getNationalite() != null) m.setNationalite(dto.getNationalite());
        if (dto.getGroupeSanguin() != null) m.setGroupeSanguin(dto.getGroupeSanguin());
        if (dto.getTaille() != null) m.setTaille(dto.getTaille());
        if (dto.getPoids() != null) m.setPoids(dto.getPoids());
        if (dto.getDiplomes() != null) {
            List<String> diplomes = Diplomes.nettoyer(dto.getDiplomes());
            m.getDiplomes().clear();
            m.getDiplomes().addAll(diplomes);
            m.setDernierDiplome(Diplomes.plusEleve(diplomes));
        } else if (dto.getDernierDiplome() != null) m.setDernierDiplome(dto.getDernierDiplome());
        if (dto.getCategorie() != null) m.setCategorie(dto.getCategorie());
        if (dto.getCompagnie() != null) m.setCompagnie(dto.getCompagnie());
        if (dto.getSection() != null) m.setSection(dto.getSection());
        if (dto.getDateIncorporation() != null) m.setDateIncorporation(dto.getDateIncorporation());
        if (dto.getDisponibleMobilisation() != null) m.setDisponibleMobilisation(dto.getDisponibleMobilisation());
    }

    private int tauxCompletude(Membre m, boolean aCompetences, boolean aParcours) {
        return CompletudeProfil.taux(m);
    }

    private String historiqueUtilisateur() {
        try {
            return perimetre.utilisateurCourant().getUsername();
        } catch (AccessDeniedException e) {
            return "system";
        }
    }

    private Membre trouver(Long id) {
        return membreRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + id));
    }

    private Cohorte trouverCohorte(Long id) {
        return cohorteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cohorte non trouvée : " + id));
    }
}
