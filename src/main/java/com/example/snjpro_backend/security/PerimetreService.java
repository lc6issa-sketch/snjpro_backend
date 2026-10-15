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
 * Les actions d'un responsable (mobilisations, actualités, notifications, activités) ne touchent que son périmètre.
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

    /** Responsable régional ou de cohorte : son action est limitée à son périmètre. */
    public boolean estResponsable(User user) {
        return user.getRole() == RoleEnum.ROLE_RESPONSABLE_REGIONAL || user.getRole() == RoleEnum.ROLE_RESPONSABLE_COHORTE;
    }

    /** Le membre fait-il partie du périmètre de l'utilisateur (sans requête : fiche déjà chargée) ? */
    public boolean couvre(User user, Membre membre) {
        return switch (user.getRole()) {
            case ROLE_ADMIN_SYSTEME, ROLE_ADMIN_SNJ, ROLE_DIRECTION -> true;
            case ROLE_RESPONSABLE_REGIONAL -> user.getRegion() != null && membre.getLocalisation() != null
                    && user.getRegion().equalsIgnoreCase(membre.getLocalisation().getRegion());
            case ROLE_RESPONSABLE_COHORTE -> user.getCohorteId() != null && membre.getCohorte() != null
                    && user.getCohorteId().equals(membre.getCohorte().getId());
            case ROLE_MEMBRE -> user.getId().equals(membre.getUserId());
            default -> false;
        };
    }

    public boolean couvre(Membre membre) { return couvre(utilisateurCourant(), membre); }

    /** Le responsable de cohorte ne voit que sa cohorte ; les autres rôles de gestion voient toutes les cohortes. */
    public boolean voitCohorte(User user, Long cohorteId) {
        return user.getRole() != RoleEnum.ROLE_RESPONSABLE_COHORTE || (cohorteId != null && cohorteId.equals(user.getCohorteId()));
    }

    /** Lève une erreur 403 si la cohorte est hors du périmètre de l'utilisateur courant. */
    public void exigerCohorte(Long cohorteId) {
        if (!voitCohorte(utilisateurCourant(), cohorteId))
            throw new AccessDeniedException("Cette cohorte ne fait pas partie de votre périmètre.");
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
