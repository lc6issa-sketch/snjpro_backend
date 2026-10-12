package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutNotification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Communication envoyée à un groupe de membres (F22, US11, US13).
 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_statut", columnList = "statut, programme_le"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private TypeNotification type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private CibleNotification cible;

    /** Régions, identifiants de cohortes ou identifiants de membres, séparés par des virgules selon la cible. */
    @Column(name = "cible_valeurs", columnDefinition = "TEXT")
    private String cibleValeurs;

    @Column(name = "canal_application", nullable = false)
    @Builder.Default
    private boolean canalApplication = true;

    @Column(name = "canal_sms", nullable = false)
    private boolean canalSms;

    @Column(name = "canal_email", nullable = false)
    private boolean canalEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private StatutNotification statut;

    @Column(name = "programme_le")
    private LocalDateTime programmeLe;

    @Column(name = "date_envoi")
    private LocalDateTime dateEnvoi;

    @Column(name = "nombre_destinataires")
    private Integer nombreDestinataires;

    @Column(name = "nombre_sms")
    private Integer nombreSms;

    /** Élément lié ouvert au clic (MOBILISATION, ACTUALITE, DEMANDE, OPPORTUNITE, DOCUMENT). */
    @Column(name = "lien_type", length = 30)
    private String lienType;

    @Column(name = "lien_id")
    private Long lienId;

    @Column(length = 50)
    private String emetteur;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() { this.dateCreation = LocalDateTime.now(); }
}
