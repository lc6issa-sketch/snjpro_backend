package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Réception d'une notification par un membre, avec son état de lecture. */
@Entity
@Table(name = "notification_destinataires", indexes = {
        @Index(name = "idx_notif_dest_membre", columnList = "membre_id, lu"),
        @Index(name = "idx_notif_dest_notification", columnList = "notification_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDestinataire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private Membre membre;

    @Column(nullable = false)
    private boolean lu;

    @Column(name = "date_lecture")
    private LocalDateTime dateLecture;
}
