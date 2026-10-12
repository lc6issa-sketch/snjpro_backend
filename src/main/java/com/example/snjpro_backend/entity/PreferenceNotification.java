package com.example.snjpro_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/** Canaux choisis par un utilisateur pour être prévenu d'un type d'événement. */
@Entity
@Table(name = "preferences_notifications", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "evenement"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreferenceNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String evenement;

    @Column(nullable = false)
    private boolean application;

    @Column(nullable = false)
    private boolean email;

    @Column(nullable = false)
    private boolean sms;
}
