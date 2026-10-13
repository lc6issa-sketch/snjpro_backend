package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.TypeMedia;
import jakarta.persistence.*;
import lombok.*;

/** Fichier (photo, vidéo ou son) d'un souvenir, stocké dans uploads/souvenirs/. */
@Entity
@Table(name = "souvenir_medias", indexes = @Index(name = "idx_media_souvenir", columnList = "souvenir_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SouvenirMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "souvenir_id", nullable = false)
    private Souvenir souvenir;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private TypeMedia type;

    @Column(nullable = false, length = 255)
    private String chemin;

    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @Column(name = "type_mime", length = 100)
    private String typeMime;

    @Column(nullable = false)
    private long taille;

    @Column(nullable = false)
    private int ordre;
}
