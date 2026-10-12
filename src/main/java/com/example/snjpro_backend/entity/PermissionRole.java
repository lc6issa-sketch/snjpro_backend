package com.example.snjpro_backend.entity;

import com.example.snjpro_backend.enumeration.RoleEnum;
import jakarta.persistence.*;
import lombok.*;

/** Droits d'un rôle sur un module (matrice « Rôles & permissions »). */
@Entity
@Table(name = "permissions_roles", uniqueConstraints = @UniqueConstraint(columnNames = {"role", "module"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(40)")
    private RoleEnum role;

    @Column(nullable = false, length = 40)
    private String module;

    @Column(nullable = false)
    private boolean voir;

    @Column(nullable = false)
    private boolean creer;

    @Column(nullable = false)
    private boolean modifier;

    @Column(nullable = false)
    private boolean supprimer;
}
