package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.TypeConversation;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Conversation de la messagerie (entre utilisateurs, groupe de cohorte ou échange avec un service du SNJ). */
@Entity
@Table(name = "conversations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private TypeConversation type;

    /** Nom affiché pour un groupe ou un service (ex. « Cohorte 2019 · Ségou », « Direction régionale · Kayes »). */
    @Column(length = 150)
    private String titre;

    /** Groupe de cohorte : cohorte concernée. */
    @Column(name = "cohorte_id")
    private Long cohorteId;

    /** Service : région de la direction régionale concernée (null = direction nationale). */
    @Column(name = "region_service", length = 100)
    private String regionService;

    @Column(name = "dernier_message_le")
    private LocalDateTime dernierMessageLe;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
