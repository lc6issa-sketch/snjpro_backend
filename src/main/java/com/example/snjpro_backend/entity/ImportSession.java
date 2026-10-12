package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.StatutImport;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Import Excel/CSV mené en plusieurs étapes (assistant : fichier, correspondance, vérification, import). */
@Entity
@Table(name = "import_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    /** Chemin relatif du fichier conservé dans le stockage. */
    @Column(nullable = false, length = 255)
    private String chemin;

    @Column(nullable = false)
    private long taille;

    @Column(name = "cohorte_id", nullable = false)
    private Long cohorteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private StatutImport statut = StatutImport.ANALYSE;

    /** JSON : en-tête du fichier -> champ SNJPro (null = colonne ignorée). */
    @Column(columnDefinition = "TEXT")
    private String correspondance;

    /** JSON : numéro de ligne -> décision sur le doublon. */
    @Column(columnDefinition = "TEXT")
    private String decisions;

    /** JSON : résultat final de l'import. */
    @Column(columnDefinition = "TEXT")
    private String resultat;

    @Column(name = "total_lignes")
    private int totalLignes;

    private int importes;
    private int fusionnes;
    private int ignores;
    private int erreurs;
    private int doublons;

    @Column(name = "cree_par", length = 50)
    private String creePar;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_import")
    private LocalDateTime dateImport;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
