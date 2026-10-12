package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MembreResponseDTO {
    private Long id;
    private String identifiantSnj;
    private String prenom;
    private String nom;
    private SexeEnum sexe;
    private LocalDate dateNaissance;
    private String lieuNaissance;
    private String numeroTelephone;
    private String email;
    private String photoUrl;
    private String telephoneSecondaire;
    private Boolean telephoneVerifie;
    private String personneAPrevenir;
    private String nationalite;
    private String groupeSanguin;
    private Double taille;
    private Double poids;
    private String dernierDiplome;
    private String categorie;
    private String compagnie;
    private String section;
    private LocalDate dateIncorporation;
    private Boolean disponibleMobilisation;
    private StatutMembreEnum statut;
    private Long cohorteId;
    private String cohorteNom;
    private Long userId;
    private SituationProfessionnelleDTO situationProfessionnelle;
    private LocalisationDTO localisation;
    private LocalDateTime dateCreation;
    private LocalDateTime dateModification;
    /** Taux de complétude du profil (0 à 100). */
    private Integer tauxCompletude;
}
