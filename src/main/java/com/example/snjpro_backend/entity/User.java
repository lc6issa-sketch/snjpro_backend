package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.RoleEnum;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "utilisateurs",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 50)
    private String prenom;

    @Column(length = 50)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40)")
    private RoleEnum role;

    @Column(length = 20)
    private String telephone;

    @Column(name = "photo_url", length = 255)
    private String photoUrl;

    /** Un code SMS est demandé à chaque nouvelle connexion. */
    @Column(name = "double_authentification")
    private Boolean doubleAuthentification;

    /** Compte créé par invitation et pas encore activé par son titulaire. */
    @Column(name = "invitation_en_attente")
    private Boolean invitationEnAttente;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    /** Périmètre d'un responsable régional : seule cette région lui est accessible. */
    @Column(length = 100)
    private String region;

    /** Périmètre d'un responsable de cohorte : seule cette cohorte lui est accessible. */
    @Column(name = "cohorte_id")
    private Long cohorteId;

    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(name = "date_modification")
    private LocalDateTime dateModification;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.dateCreation = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.dateModification = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return actif; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return actif; }
}
