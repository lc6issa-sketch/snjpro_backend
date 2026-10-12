package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Dossier de classement des documents (Textes officiels, Formulaires, Guides…). */
@Entity
@Table(name = "dossiers_documents", uniqueConstraints = @UniqueConstraint(columnNames = "nom"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibilite_defaut", nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private VisibiliteDocument visibiliteDefaut = VisibiliteDocument.MEMBRES;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
