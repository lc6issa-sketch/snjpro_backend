package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.ApparenceDTO;
import com.example.snjpro_backend.serviceImpl.ApparenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apparence")
@RequiredArgsConstructor
@Tag(name = "09. Référentiels", description = "Listes de valeurs pour les formulaires et filtres")
public class ApparenceController {

    private final ApparenceService service;

    @GetMapping
    @Operation(summary = "Logo de l'application et charte associée (public : utilisé dès la page de connexion)")
    public ResponseEntity<ApparenceDTO> apparence() {
        return ResponseEntity.ok(service.apparence());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN_SYSTEME')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Changer le logo de l'application (VERT ou OR) ; la charte graphique suit le logo")
    public ResponseEntity<ApparenceDTO> changer(@Valid @RequestBody ApparenceDTO dto) {
        return ResponseEntity.ok(service.changer(dto));
    }
}
