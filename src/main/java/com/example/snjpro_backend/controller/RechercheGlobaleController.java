package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.CohorteDTO;
import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.dto.MembreResponseDTO;
import com.example.snjpro_backend.dto.UserResponseDTO;
import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.serviceInterface.CohorteServiceInterface;
import com.example.snjpro_backend.serviceInterface.RechercheImportServiceInterface;
import com.example.snjpro_backend.serviceInterface.UserServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "07. Recherche, import & doublons")
@SecurityRequirement(name = "bearerAuth")
public class RechercheGlobaleController {

    private final RechercheImportServiceInterface rechercheService;
    private final UserServiceInterface userService;
    private final CohorteServiceInterface cohorteService;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Resultats {
        private String recherche;
        private List<MembreResponseDTO> membres;
        private long totalMembres;
        private List<UserResponseDTO> utilisateurs;
        private List<CohorteDTO> cohortes;
    }

    @GetMapping("/api/recherche")
    @PreAuthorize("hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')")
    @Operation(summary = "Recherche globale (palette Ctrl K) : membres, utilisateurs et cohortes correspondant au texte saisi")
    public ResponseEntity<Resultats> rechercher(@RequestParam("q") String q,
                                                @RequestParam(defaultValue = "5") int limite,
                                                Authentication auth) {
        String texte = q == null ? "" : q.trim();
        if (texte.length() < 2)
            return ResponseEntity.ok(Resultats.builder().recherche(texte).membres(List.of()).utilisateurs(List.of()).cohortes(List.of()).build());
        MembreFilterDTO filtre = new MembreFilterDTO();
        filtre.setQuery(texte);
        var membres = rechercheService.rechercheAvancee(filtre, PageRequest.of(0, Math.max(1, Math.min(limite, 20))));
        RoleEnum role = ((User) auth.getPrincipal()).getRole();
        String t = texte.toLowerCase();
        List<UserResponseDTO> utilisateurs = (role == RoleEnum.ROLE_ADMIN_SYSTEME || role == RoleEnum.ROLE_ADMIN_SNJ)
                ? userService.obtenirTousLesUtilisateurs(null, null).stream()
                        .filter(u -> u.getRole() != RoleEnum.ROLE_MEMBRE)
                        .filter(u -> (u.getUsername() + " " + u.getEmail() + " " + u.getPrenom() + " " + u.getNom()).toLowerCase().contains(t))
                        .limit(3).toList()
                : List.of();
        List<CohorteDTO> cohortes = cohorteService.obtenirToutesLesCohortes(null, texte).stream().limit(3).toList();
        return ResponseEntity.ok(Resultats.builder().recherche(texte).membres(membres.getContent())
                .totalMembres(membres.getTotalElements()).utilisateurs(utilisateurs).cohortes(cohortes).build());
    }
}
