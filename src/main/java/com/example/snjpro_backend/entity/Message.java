package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Message d'une conversation, éventuellement accompagné d'une pièce jointe. */
@Entity
@Table(name = "messages", indexes = @Index(name = "idx_message_conversation", columnList = "conversation_id, date_envoi"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(columnDefinition = "TEXT")
    private String contenu;

    @Column(name = "piece_jointe_chemin", length = 255)
    private String pieceJointeChemin;

    @Column(name = "piece_jointe_nom", length = 255)
    private String pieceJointeNom;

    @Column(name = "piece_jointe_taille")
    private Long pieceJointeTaille;

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;
}
