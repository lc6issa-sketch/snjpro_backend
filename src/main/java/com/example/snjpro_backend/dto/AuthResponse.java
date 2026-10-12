package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private String type;
    private Long expiresInMs;
    private Long userId;
    private String username;
    private String email;
    private String prenom;
    private String nom;
    private RoleEnum role;
    private String region;
    private Long cohorteId;
    /** Fiche membre liée au compte (rôle MEMBRE). */
    private Long membreId;
    private String photoUrl;
    /** Téléphone du compte (paramètres, double authentification). */
    private String telephone;
    private Boolean doubleAuthentification;
    /** true : le mot de passe est correct mais un code SMS doit encore être saisi (POST /api/auth/login/code/verifier). */
    private Boolean deuxiemeFacteurRequis;
    private String telephoneMasque;
    private String message;

    public static AuthResponse of(String token, Long expiresInMs, User user) {
        return AuthResponse.builder()
                .token(token)
                .type(token != null ? "Bearer" : null)
                .expiresInMs(expiresInMs)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .prenom(user.getPrenom())
                .nom(user.getNom())
                .role(user.getRole())
                .region(user.getRegion())
                .cohorteId(user.getCohorteId())
                .photoUrl(user.getPhotoUrl())
                .telephone(user.getTelephone())
                .doubleAuthentification(Boolean.TRUE.equals(user.getDoubleAuthentification()))
                .build();
    }
}
