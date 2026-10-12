package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Participant d'une conversation et date de sa dernière lecture. */
@Entity
@Table(name = "participants_conversation", uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}),
        indexes = @Index(name = "idx_participant_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParticipantConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "derniere_lecture")
    private LocalDateTime derniereLecture;
}
