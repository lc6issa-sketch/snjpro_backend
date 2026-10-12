package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.RoleEnum;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String prenom;
    private String nom;
    private RoleEnum role;
    private boolean actif;
    private String region;
    private Long cohorteId;
    private LocalDateTime derniereConnexion;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    private String createdBy;
    private String telephone;
    private String photoUrl;
    /** Invité mais pas encore activé (statut « Invité »). */
    private boolean invitationEnAttente;
    private boolean doubleAuthentification;
}
