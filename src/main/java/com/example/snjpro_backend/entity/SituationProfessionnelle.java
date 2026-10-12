package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "situations_professionnelles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SituationProfessionnelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutProfessionnelEnum statut = StatutProfessionnelEnum.AUTRE;

    @Column(length = 100)
    private String profession;

    @Column(name = "secteur_activite", length = 100)
    private String secteurActivite;

    @Column(name = "nom_employeur", length = 150)
    private String nomEmployeur;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_debut_activite")
    private LocalDate dateDebutActivite;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @PrePersist
    @PreUpdate
    protected void onUpdate() { this.dateModification = LocalDateTime.now(); }
}
