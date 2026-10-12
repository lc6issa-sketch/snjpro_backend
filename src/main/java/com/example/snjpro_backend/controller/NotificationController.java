package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.NotificationDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.serviceInterface.NotificationServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "12. Notifications & alertes", description = "Communications aux membres (F22, US11) et panneau d'alertes de l'application web")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private static final String EMETTEURS = "hasAnyRole('ADMIN_SYSTEME', 'ADMIN_SNJ', 'RESPONSABLE_REGIONAL', 'RESPONSABLE_COHORTE')";

    private final NotificationServiceInterface service;

    // ---------------------------------------------------------------- Envoi aux membres

    @PostMapping("/api/notifications")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Envoyer, programmer ou enregistrer en brouillon une notification",
            description = "Cible TOUS, REGION, COHORTE ou SELECTION ; canaux application / SMS / e-mail. "
                    + "Un responsable régional ou de cohorte est limité à son périmètre.")
    public ResponseEntity<Resume> envoyer(@Valid @RequestBody Envoi dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.envoyer(dto));
    }

    @GetMapping("/api/notifications")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Historique des envois (avec taux de lecture)")
    public ResponseEntity<Page<Resume>> historique(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.historique(pageable));
    }

    @GetMapping("/api/notifications/statistiques")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Indicateurs : envoyées ce mois, taux de lecture moyen, SMS envoyés")
    public ResponseEntity<Statistiques> statistiques() {
        return ResponseEntity.ok(service.statistiques());
    }

    @GetMapping("/api/notifications/estimation")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Nombre de destinataires et de SMS pour une cible donnée (avant l'envoi)")
    public ResponseEntity<Estimation> estimer(@RequestParam CibleNotification cible,
                                              @RequestParam(required = false) List<String> valeurs,
                                              @RequestParam(defaultValue = "false") boolean sms) {
        return ResponseEntity.ok(service.estimer(cible, valeurs, sms));
    }

    @GetMapping("/api/notifications/{id}")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Détail d'un envoi")
    public ResponseEntity<Resume> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @PutMapping("/api/notifications/{id}")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Modifier un brouillon ou un envoi programmé")
    public ResponseEntity<Resume> modifier(@PathVariable Long id, @Valid @RequestBody Envoi dto) {
        return ResponseEntity.ok(service.modifierBrouillon(id, dto));
    }

    @PostMapping("/api/notifications/{id}/envoyer")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Envoyer maintenant un brouillon ou un envoi programmé")
    public ResponseEntity<Resume> envoyerBrouillon(@PathVariable Long id) {
        return ResponseEntity.ok(service.envoyerBrouillon(id));
    }

    @DeleteMapping("/api/notifications/{id}")
    @PreAuthorize(EMETTEURS)
    @Operation(summary = "Supprimer un brouillon ou un envoi programmé")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Espace membre (application mobile)

    @GetMapping("/api/mon-espace/notifications")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Mes notifications (filtres : non lues, type MOBILISATION…)")
    public ResponseEntity<Page<MaNotification>> mesNotifications(
            @RequestParam(defaultValue = "false") boolean nonLues,
            @RequestParam(required = false) com.example.snjpro_backend.enumeration.Enumerations.TypeNotification type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.mesNotifications(nonLues, type, pageable));
    }

    @GetMapping("/api/mon-espace/notifications/non-lues")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<Map<String, Long>> nonLues() {
        return ResponseEntity.ok(Map.of("nonLues", service.compterNonLues()));
    }

    @PatchMapping("/api/mon-espace/notifications/{id}/lu")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<MaNotification> marquerLue(@PathVariable Long id) {
        return ResponseEntity.ok(service.marquerLue(id));
    }

    @PatchMapping("/api/mon-espace/notifications/tout-lu")
    @PreAuthorize("hasRole('MEMBRE')")
    @Operation(summary = "Tout lire")
    public ResponseEntity<Map<String, Integer>> toutLu() {
        return ResponseEntity.ok(Map.of("misesAJour", service.toutMarquerLu()));
    }

    // ---------------------------------------------------------------- Alertes (cloche de l'application web)

    @GetMapping("/api/alertes")
    @Operation(summary = "Mes alertes (panneau de notifications web)")
    public ResponseEntity<Page<Alerte>> alertes(@RequestParam(defaultValue = "false") boolean nonLues,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.mesAlertes(nonLues, pageable));
    }

    @GetMapping("/api/alertes/non-lues")
    @Operation(summary = "Nombre d'alertes non lues (pastille de la cloche)")
    public ResponseEntity<Map<String, Long>> alertesNonLues() {
        return ResponseEntity.ok(Map.of("nonLues", service.compterAlertesNonLues()));
    }

    @PatchMapping("/api/alertes/{id}/lu")
    @Operation(summary = "Marquer une alerte comme lue")
    public ResponseEntity<Alerte> alerteLue(@PathVariable Long id) {
        return ResponseEntity.ok(service.marquerAlerteLue(id));
    }

    @PatchMapping("/api/alertes/tout-lu")
    @Operation(summary = "Tout marquer comme lu")
    public ResponseEntity<Map<String, Integer>> toutesLues() {
        return ResponseEntity.ok(Map.of("misesAJour", service.toutesAlertesLues()));
    }
}
