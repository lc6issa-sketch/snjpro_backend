package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Document publié par le SNJ (F27) ou document personnel d'un membre (pièce d'identité, diplôme, attestation…).
 */
@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_document_membre", columnList = "membre_id"),
        @Index(name = "idx_document_dossier", columnList = "dossier_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(nullable = false, length = 255)
    private String chemin;

    @Column(name = "type_mime", length = 100)
    private String typeMime;

    @Column(nullable = false)
    private long taille;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id")
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private VisibiliteDocument visibilite = VisibiliteDocument.MEMBRES;

    /** Renseigné pour un document personnel : seul ce membre (et les gestionnaires) y a accès. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id")
    private Membre membre;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private TypeDocument typeDocument = TypeDocument.AUTRE;

    /** Pièce contrôlée par un agent. */
    @Column(nullable = false)
    private boolean verifie;

    @Column(name = "ajoute_par", length = 50)
    private String ajoutePar;

    @Column(name = "date_ajout", nullable = false, updatable = false)
    private LocalDateTime dateAjout;

    @PrePersist
    protected void onCreate() { this.dateAjout = LocalDateTime.now(); }
}
