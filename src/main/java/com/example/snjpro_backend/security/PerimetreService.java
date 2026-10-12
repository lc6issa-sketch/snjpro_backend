package com.example.snjpro_backend.security;

import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.repository.MembreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applique le périmètre d'accès de l'utilisateur connecté :
 * « un utilisateur ne doit accéder qu'aux informations nécessaires à ses fonctions ».
 * <ul>
 *     <li>Administrateurs : accès complet ;</li>
 *     <li>Responsable régional : membres localisés dans sa région ;</li>
 *     <li>Responsable de cohorte : membres de sa cohorte ;</li>
 *     <li>Membre : uniquement sa propre fiche.</li>
 * </ul>
 * Exposé sous le nom {@code perimetre} pour être utilisé dans les expressions {@code @PreAuthorize}.
 */
@Service("perimetre")
@RequiredArgsConstructor
public class PerimetreService {
    private final MembreRepository membreRepository;

    public User utilisateurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user))
            throw new AccessDeniedException("Utilisateur non authentifié.");
        return user;
    }

    /** Restreint les critères de recherche au périmètre de l'utilisateur courant. */
    public MembreFilterDTO restreindre(MembreFilterDTO filter) {
        User user = utilisateurCourant();
        switch (user.getRole()) {
            case ROLE_RESPONSABLE_REGIONAL -> {
                if (user.getRegion() == null)
                    throw new AccessDeniedException("Aucune région n'est associée à ce responsable régional.");
                filter.setRegion(user.getRegion());
            }
            case ROLE_RESPONSABLE_COHORTE -> {
                if (user.getCohorteId() == null)
                    throw new AccessDeniedException("Aucune cohorte n'est associée à ce responsable de cohorte.");
                filter.setCohorteId(user.getCohorteId());
            }
            case ROLE_ADMIN_SYSTEME, ROLE_ADMIN_SNJ -> { }
            default -> throw new AccessDeniedException("Accès à la liste des membres non autorisé.");
        }
        return filter;
    }

    /** Vérifie que l'utilisateur courant peut consulter / gérer la fiche du membre. */
    @Transactional(readOnly = true)
    public boolean peutAcceder(Long membreId) {
        User user = utilisateurCourant();
        RoleEnum role = user.getRole();
        if (role == RoleEnum.ROLE_ADMIN_SYSTEME || role == RoleEnum.ROLE_ADMIN_SNJ) return true;
        Membre membre = membreRepository.findById(membreId).orElse(null);
        if (membre == null) return true; // laisse le service renvoyer un 404 explicite
        return switch (role) {
            case ROLE_RESPONSABLE_REGIONAL -> user.getRegion() != null
                    && membre.getLocalisation() != null
                    && user.getRegion().equalsIgnoreCase(membre.getLocalisation().getRegion());
            case ROLE_RESPONSABLE_COHORTE -> user.getCohorteId() != null
                    && user.getCohorteId().equals(membre.getCohorte().getId());
            case ROLE_MEMBRE -> user.getId().equals(membre.getUserId());
            default -> false;
        };
    }

    /** Vérifie l'accès à une fiche identifiée par son identifiant SNJ. */
    @Transactional(readOnly = true)
    public boolean peutAccederParIdentifiant(String identifiantSnj) {
        return membreRepository.findByIdentifiantSnj(identifiantSnj)
                .map(m -> peutAcceder(m.getId()))
                .orElse(true);
    }

    /** Retourne la fiche membre liée au compte connecté (rôle MEMBRE). */
    @Transactional(readOnly = true)
    public Long membreIdCourant() {
        User user = utilisateurCourant();
        return membreRepository.findByUserId(user.getId())
                .map(Membre::getId)
                .orElseThrow(() -> new AccessDeniedException("Aucune fiche membre n'est liée à ce compte."));
    }
}
