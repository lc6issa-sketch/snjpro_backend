package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.RoleDTOs.Permission;
import com.example.snjpro_backend.dto.RoleDTOs.Role;
import com.example.snjpro_backend.entity.PermissionRole;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.repository.PermissionRoleRepository;
import com.example.snjpro_backend.repository.UserRepository;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Matrice des permissions par rôle et par module (écran « Rôles & permissions »).
 * <p>
 * Les valeurs par défaut reflètent les contrôles d'accès appliqués par l'API ; la matrice modifiée est
 * enregistrée et exposée aux applications (affichage / masquage des menus et boutons).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {
    public static final Map<String, String> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("MEMBRES", "Membres");
        MODULES.put("COHORTES", "Cohortes");
        MODULES.put("COMPETENCES", "Compétences");
        MODULES.put("MOBILISATION", "Mobilisation");
        MODULES.put("NOTIFICATIONS", "Notifications");
        MODULES.put("ACTUALITES", "Actualités");
        MODULES.put("OPPORTUNITES", "Opportunités");
        MODULES.put("DOCUMENTS", "Documents");
        MODULES.put("DEMANDES", "Demandes");
        MODULES.put("ACTIVITES", "Activités & présences");
        MODULES.put("SOUVENIRS", "Souvenirs de formation");
        MODULES.put("IMPORT", "Import & doublons");
        MODULES.put("STATISTIQUES", "Statistiques & rapports");
        MODULES.put("HISTORIQUE", "Historique");
        MODULES.put("UTILISATEURS", "Utilisateurs & rôles");
    }

    private final PermissionRoleRepository repository;
    private final UserRepository userRepository;
    private final HistoriqueServiceInterface historiqueService;

    @Transactional(readOnly = true)
    public List<Role> roles() {
        return Arrays.stream(RoleEnum.values()).map(this::role).toList();
    }

    @Transactional(readOnly = true)
    public Role role(RoleEnum role) {
        List<Permission> permissions = permissions(role);
        return Role.builder()
                .role(role).libelle(libelle(role)).description(description(role)).perimetre(perimetre(role))
                .nombreUtilisateurs(userRepository.findByRole(role).size())
                .modulesAccessibles(permissions.stream().filter(Permission::isVoir).map(Permission::getLibelle).toList())
                .permissions(permissions)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Permission> permissions(RoleEnum role) {
        Map<String, PermissionRole> enregistrees = new HashMap<>();
        repository.findByRole(role).forEach(p -> enregistrees.put(p.getModule(), p));
        List<Permission> l = new ArrayList<>();
        MODULES.forEach((module, libelle) -> {
            PermissionRole p = enregistrees.get(module);
            boolean[] d = p != null ? new boolean[]{p.isVoir(), p.isCreer(), p.isModifier(), p.isSupprimer()} : defaut(role, module);
            l.add(Permission.builder().module(module).libelle(libelle).voir(d[0]).creer(d[1]).modifier(d[2]).supprimer(d[3]).build());
        });
        return l;
    }

    public Role enregistrer(RoleEnum role, List<Permission> permissions) {
        if (role == RoleEnum.ROLE_ADMIN_SYSTEME)
            throw new IllegalArgumentException("Les droits de l'administrateur système ne sont pas modifiables.");
        for (Permission p : permissions) {
            if (!MODULES.containsKey(p.getModule())) throw new IllegalArgumentException("Module inconnu : " + p.getModule());
            PermissionRole e = repository.findByRoleAndModule(role, p.getModule())
                    .orElseGet(() -> PermissionRole.builder().role(role).module(p.getModule()).build());
            boolean voir = p.isVoir() || p.isCreer() || p.isModifier() || p.isSupprimer();
            e.setVoir(voir);
            e.setCreer(p.isCreer());
            e.setModifier(p.isModifier());
            e.setSupprimer(p.isSupprimer());
            repository.save(e);
        }
        historiqueService.enregistrer("MODIFICATION_PERMISSIONS", "ROLE", null, role.name());
        return role(role);
    }

    /** Droits par défaut : voir, créer, modifier, supprimer. */
    private static boolean[] defaut(RoleEnum role, String module) {
        boolean t = true, f = false;
        return switch (role) {
            case ROLE_ADMIN_SYSTEME -> new boolean[]{t, t, t, t};
            case ROLE_ADMIN_SNJ -> module.equals("UTILISATEURS") ? new boolean[]{t, f, f, f} : new boolean[]{t, t, t, !module.equals("MEMBRES")};
            case ROLE_DIRECTION -> switch (module) {
                case "STATISTIQUES" -> new boolean[]{t, t, f, f};
                case "ACTUALITES" -> new boolean[]{t, t, t, f};
                case "SOUVENIRS" -> new boolean[]{t, f, f, f};
                case "COMPETENCES", "DOCUMENTS" -> new boolean[]{t, f, f, f};
                default -> new boolean[]{f, f, f, f};
            };
            case ROLE_RESPONSABLE_REGIONAL -> switch (module) {
                case "MEMBRES" -> new boolean[]{t, f, t, f};
                case "MOBILISATION" -> new boolean[]{t, t, t, t};
                case "NOTIFICATIONS", "DEMANDES", "ACTUALITES" -> new boolean[]{t, t, t, f};
                case "ACTIVITES", "SOUVENIRS" -> new boolean[]{t, t, t, f};
                case "COHORTES", "COMPETENCES", "STATISTIQUES", "DOCUMENTS" -> new boolean[]{t, f, f, f};
                default -> new boolean[]{f, f, f, f};
            };
            case ROLE_RESPONSABLE_COHORTE -> switch (module) {
                case "MEMBRES" -> new boolean[]{t, t, t, f};
                case "ACTIVITES", "NOTIFICATIONS", "DEMANDES", "MOBILISATION", "SOUVENIRS", "ACTUALITES" -> new boolean[]{t, t, t, f};
                case "COHORTES", "COMPETENCES", "STATISTIQUES", "DOCUMENTS" -> new boolean[]{t, f, f, f};
                default -> new boolean[]{f, f, f, f};
            };
            case ROLE_PARTENAIRE -> module.equals("OPPORTUNITES") ? new boolean[]{t, t, t, f} : new boolean[]{f, f, f, f};
            case ROLE_MEMBRE -> new boolean[]{f, f, f, f};
        };
    }

    public static String libelle(RoleEnum r) {
        return switch (r) {
            case ROLE_ADMIN_SYSTEME -> "Administrateur";
            case ROLE_ADMIN_SNJ -> "Agent SNJ";
            case ROLE_DIRECTION -> "Direction";
            case ROLE_RESPONSABLE_REGIONAL -> "Responsable régional";
            case ROLE_RESPONSABLE_COHORTE -> "Responsable de cohorte";
            case ROLE_MEMBRE -> "Membre";
            case ROLE_PARTENAIRE -> "Partenaire";
        };
    }

    private static String description(RoleEnum r) {
        return switch (r) {
            case ROLE_ADMIN_SYSTEME -> "Gère les comptes, les rôles et les paramètres de la plateforme.";
            case ROLE_ADMIN_SNJ -> "Gère les membres, cohortes, imports et communications.";
            case ROLE_DIRECTION -> "Consulte le tableau de bord, les statistiques et les rapports.";
            case ROLE_RESPONSABLE_REGIONAL -> "Gère les membres de sa région ; ses mobilisations, actualités et notifications ne concernent que sa région.";
            case ROLE_RESPONSABLE_COHORTE -> "Suit les membres de sa cohorte ; ses mobilisations, actualités et notifications ne concernent que sa cohorte.";
            case ROLE_MEMBRE -> "Consulte son profil et répond aux sollicitations depuis l'application mobile.";
            case ROLE_PARTENAIRE -> "Publie des opportunités d'emploi, de stage ou de formation.";
        };
    }

    private static String perimetre(RoleEnum r) {
        return switch (r) {
            case ROLE_RESPONSABLE_REGIONAL -> "Région de rattachement";
            case ROLE_RESPONSABLE_COHORTE -> "Cohorte de rattachement";
            case ROLE_MEMBRE -> "Sa propre fiche";
            default -> "National";
        };
    }
}
