package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Réglage global de l'application (clé / valeur), par exemple le logo et sa charte graphique. */
@Entity
@Table(name = "parametres_application")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParametreApplication {

    @Id
    @Column(length = 50)
    private String cle;

    @Column(length = 255)
    private String valeur;

    @Column(name = "modifie_par", length = 50)
    private String modifiePar;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @PrePersist @PreUpdate
    protected void onSave() { this.dateModification = LocalDateTime.now(); }
}
