package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class RoleDTOs {
    private RoleDTOs() { }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Permission {
        private String module;
        private String libelle;
        private boolean voir;
        private boolean creer;
        private boolean modifier;
        private boolean supprimer;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Role {
        private RoleEnum role;
        private String libelle;
        private String description;
        private String perimetre;
        private long nombreUtilisateurs;
        /** Modules accessibles en lecture (résumé affiché sur la carte du rôle). */
        private List<String> modulesAccessibles;
        private List<Permission> permissions;
    }

    @Data
    public static class Permissions {
        private List<Permission> permissions;
    }
}
