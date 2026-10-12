package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.NiveauCompetenceEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "membre_competences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembreCompetence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Column(nullable = false, length = 100)
    private String intitule;

    @Column(length = 100)
    private String categorie;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NiveauCompetenceEnum niveau;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_ajout", nullable = false, updatable = false)
    private LocalDateTime dateAjout;

    @PrePersist
    protected void onCreate() { this.dateAjout = LocalDateTime.now(); }

}
