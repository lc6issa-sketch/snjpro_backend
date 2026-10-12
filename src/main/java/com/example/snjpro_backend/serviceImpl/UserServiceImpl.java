package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.ChangementMotDePasseDTO;
import com.example.snjpro_backend.dto.UserDTO;
import com.example.snjpro_backend.dto.UserResponseDTO;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.repository.CohorteRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.repository.UserRepository;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.UserServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserServiceInterface {
    private static final String ENTITE = "UTILISATEUR";

    private final UserRepository userRepository;
    private final CohorteRepository cohorteRepository;
    private final MembreRepository membreRepository;
    private final PasswordEncoder passwordEncoder;
    private final HistoriqueServiceInterface historiqueService;

    @Override
    public UserResponseDTO creerUtilisateur(UserDTO dto, String createdBy) {
        if (!StringUtils.hasText(dto.getPassword()))
            throw new IllegalArgumentException("Le mot de passe est obligatoire à la création.");
        if (userRepository.existsByUsername(dto.getUsername()))
            throw new ConflitException("Le nom d'utilisateur '" + dto.getUsername() + "' est déjà utilisé.");
        if (userRepository.existsByEmail(dto.getEmail()))
            throw new ConflitException("L'email '" + dto.getEmail() + "' est déjà utilisé.");
        verifierPerimetre(dto);
        User user = User.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .email(dto.getEmail())
                .prenom(dto.getPrenom())
                .nom(dto.getNom())
                .role(dto.getRole())
                .region(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? dto.getRegion() : null)
                .cohorteId(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE ? dto.getCohorteId() : null)
                .actif(true)
                .createdBy(createdBy)
                .build();
        user = userRepository.save(user);
        historiqueService.enregistrer("CREATION", ENTITE, user.getId(), "Rôle : " + user.getRole());
        return toResponseDTO(user);
    }

    @Override
    public UserResponseDTO modifierUtilisateur(Long id, UserDTO dto, String utilisateurCourant) {
        User user = trouver(id);
        if (!user.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail()))
            throw new ConflitException("L'email '" + dto.getEmail() + "' est déjà utilisé.");
        if (user.getUsername().equals(utilisateurCourant) && user.getRole() != dto.getRole())
            throw new IllegalArgumentException("Vous ne pouvez pas modifier votre propre rôle.");
        verifierPerimetre(dto);
        user.setEmail(dto.getEmail());
        user.setPrenom(dto.getPrenom());
        user.setNom(dto.getNom());
        user.setRole(dto.getRole());
        user.setRegion(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL ? dto.getRegion() : null);
        user.setCohorteId(dto.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE ? dto.getCohorteId() : null);
        if (StringUtils.hasText(dto.getPassword()))
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        historiqueService.enregistrer("MODIFICATION", ENTITE, id, "Rôle : " + dto.getRole());
        return toResponseDTO(userRepository.save(user));
    }

    @Override @Transactional(readOnly = true)
    public UserResponseDTO obtenirUtilisateur(Long id) {
        return toResponseDTO(trouver(id));
    }

    @Override @Transactional(readOnly = true)
    public UserResponseDTO obtenirParUsername(String username) {
        return userRepository.findByUsername(username).map(this::toResponseDTO)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + username));
    }

    @Override @Transactional(readOnly = true)
    public UserResponseDTO obtenirParEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase()).or(() -> userRepository.findByEmail(email.trim()))
                .map(this::toResponseDTO)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + email));
    }

    @Override @Transactional(readOnly = true)
    public List<UserResponseDTO> obtenirTousLesUtilisateurs(RoleEnum role, Boolean actif) {
        return userRepository.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> actif == null || u.isActif() == actif)
                .map(this::toResponseDTO)
                .toList();
    }

    @Override
    public UserResponseDTO activerDesactiverUtilisateur(Long id, boolean actif, String utilisateurCourant) {
        User user = trouver(id);
        if (!actif && user.getUsername().equals(utilisateurCourant))
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte.");
        user.setActif(actif);
        historiqueService.enregistrer(actif ? "ACTIVATION" : "DESACTIVATION", ENTITE, id, null);
        return toResponseDTO(userRepository.save(user));
    }

    @Override
    public void supprimerUtilisateur(Long id, String utilisateurCourant) {
        User user = trouver(id);
        if (user.getUsername().equals(utilisateurCourant))
            throw new IllegalArgumentException("Vous ne pouvez pas supprimer votre propre compte.");
        // Détache la fiche membre éventuellement liée au compte
        membreRepository.findByUserId(id).ifPresent(m -> m.setUserId(null));
        userRepository.delete(user);
        historiqueService.enregistrer("SUPPRESSION", ENTITE, id, "Utilisateur : " + user.getUsername());
    }

    @Override
    public void changerMotDePasse(String username, ChangementMotDePasseDTO dto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + username));
        if (!passwordEncoder.matches(dto.getAncienMotDePasse(), user.getPassword()))
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect.");
        user.setPassword(passwordEncoder.encode(dto.getNouveauMotDePasse()));
        userRepository.save(user);
        historiqueService.enregistrer("CHANGEMENT_MOT_DE_PASSE", ENTITE, user.getId(), null);
    }

    @Override
    public UserResponseDTO reinitialiserMotDePasse(Long id, String nouveauMotDePasse) {
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8)
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 8 caractères.");
        User user = trouver(id);
        user.setPassword(passwordEncoder.encode(nouveauMotDePasse));
        historiqueService.enregistrer("REINITIALISATION_MOT_DE_PASSE", ENTITE, id, null);
        return toResponseDTO(userRepository.save(user));
    }

    @Override
    public void enregistrerConnexion(Long id) {
        userRepository.findById(id).ifPresent(u -> u.setDerniereConnexion(LocalDateTime.now()));
    }

    @Override @Transactional(readOnly = true)
    public boolean existeParUsername(String username) { return userRepository.existsByUsername(username); }

    @Override @Transactional(readOnly = true)
    public boolean existeParEmail(String email) { return userRepository.existsByEmail(email); }

    private User trouver(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé : " + id));
    }

    /** Un responsable régional doit avoir une région, un responsable de cohorte une cohorte existante. */
    private void verifierPerimetre(UserDTO dto) {
        if (dto.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL && !StringUtils.hasText(dto.getRegion()))
            throw new IllegalArgumentException("La région est obligatoire pour un responsable régional.");
        if (dto.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE) {
            if (dto.getCohorteId() == null)
                throw new IllegalArgumentException("La cohorte est obligatoire pour un responsable de cohorte.");
            if (!cohorteRepository.existsById(dto.getCohorteId()))
                throw new EntityNotFoundException("Cohorte non trouvée : " + dto.getCohorteId());
        }
    }

    private UserResponseDTO toResponseDTO(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPrenom(user.getPrenom());
        dto.setNom(user.getNom());
        dto.setRole(user.getRole());
        dto.setActif(user.isActif());
        dto.setRegion(user.getRegion());
        dto.setCohorteId(user.getCohorteId());
        dto.setDerniereConnexion(user.getDerniereConnexion());
        dto.setDateCreation(user.getDateCreation());
        dto.setDateModification(user.getDateModification());
        dto.setCreatedBy(user.getCreatedBy());
        dto.setTelephone(user.getTelephone());
        dto.setPhotoUrl(user.getPhotoUrl() != null ? "/api/auth/utilisateurs/" + user.getId() + "/photo" : null);
        dto.setInvitationEnAttente(Boolean.TRUE.equals(user.getInvitationEnAttente()));
        dto.setDoubleAuthentification(Boolean.TRUE.equals(user.getDoubleAuthentification()));
        return dto;
    }
}
