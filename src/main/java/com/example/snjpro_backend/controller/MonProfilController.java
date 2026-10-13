package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.dto.AuthAvanceDTOs.Code;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.EspaceMembreDTOs.*;
import com.example.snjpro_backend.serviceInterface.AuthAvanceServiceInterface;
import com.example.snjpro_backend.serviceInterface.EspaceMembreServiceInterface;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.CompetenceParcoursSNJServiceInterface;
import com.example.snjpro_backend.serviceInterface.LocalisationServiceInterface;
import com.example.snjpro_backend.serviceInterface.MembreServiceInterface;
import com.example.snjpro_backend.serviceInterface.SituationProfessionnelleServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Espace personnel du membre connecté (F17 / US05) : il ne peut agir que sur sa propre fiche,
 * et uniquement sur les champs autorisés.
 */
@RestController
@RequestMapping("/api/mon-profil")
@RequiredArgsConstructor
@Tag(name = "06. Mon profil (membre)", description = "Consultation et mise à jour de son propre profil par un membre connecté")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('MEMBRE')")
public class MonProfilController {

    private final PerimetreService perimetre;
    private final MembreServiceInterface membreService;
    private final SituationProfessionnelleServiceInterface situationService;
    private final LocalisationServiceInterface localisationService;
    private final CompetenceParcoursSNJServiceInterface competenceParcoursService;
    private final EspaceMembreServiceInterface espaceService;
    private final AuthAvanceServiceInterface authAvanceService;

    @GetMapping
    @Operation(summary = "Consulter mon profil complet")
    public ResponseEntity<ProfilMembreDTO> monProfil() {
        return ResponseEntity.ok(membreService.obtenirProfilComplet(perimetre.membreIdCourant()));
    }

    @PatchMapping
    @Operation(summary = "Mettre à jour mes coordonnées (téléphone, email, photo)")
    public ResponseEntity<MembreResponseDTO> mettreAJour(@Valid @RequestBody MiseAJourProfilDTO dto) {
        return ResponseEntity.ok(membreService.mettreAJourProfil(perimetre.membreIdCourant(), dto));
    }

    @PutMapping("/situation-professionnelle")
    @Operation(summary = "Renseigner ma situation professionnelle")
    public ResponseEntity<SituationProfessionnelleDTO> situation(@Valid @RequestBody SituationProfessionnelleDTO dto) {
        return ResponseEntity.ok(situationService.modifier(perimetre.membreIdCourant(), dto));
    }

    @PutMapping("/localisation")
    @Operation(summary = "Renseigner ma localisation actuelle")
    public ResponseEntity<LocalisationDTO> localisation(@Valid @RequestBody LocalisationDTO dto) {
        return ResponseEntity.ok(localisationService.modifier(perimetre.membreIdCourant(), dto));
    }

    @GetMapping("/competences")
    @Operation(summary = "Lister mes compétences")
    public ResponseEntity<List<CompetenceDTO>> competences() {
        return ResponseEntity.ok(competenceParcoursService.obtenirCompetencesMembre(perimetre.membreIdCourant()));
    }

    @PostMapping("/competences")
    @Operation(summary = "Ajouter une compétence")
    public ResponseEntity<CompetenceDTO> ajouterCompetence(@Valid @RequestBody CompetenceDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(competenceParcoursService.ajouterCompetence(perimetre.membreIdCourant(), dto));
    }

    @PutMapping("/competences/{competenceId}")
    @Operation(summary = "Modifier une de mes compétences")
    public ResponseEntity<CompetenceDTO> modifierCompetence(@PathVariable Long competenceId,
                                                            @Valid @RequestBody CompetenceDTO dto) {
        return ResponseEntity.ok(competenceParcoursService.modifierCompetence(perimetre.membreIdCourant(), competenceId, dto));
    }

    @DeleteMapping("/competences/{competenceId}")
    @Operation(summary = "Supprimer une de mes compétences")
    public ResponseEntity<Void> supprimerCompetence(@PathVariable Long competenceId) {
        competenceParcoursService.supprimerCompetence(perimetre.membreIdCourant(), competenceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parcours")
    @Operation(summary = "Consulter mon parcours SNJ (lecture seule)")
    public ResponseEntity<List<ParcoursSNJDTO>> parcours() {
        return ResponseEntity.ok(competenceParcoursService.obtenirParcoursMembre(perimetre.membreIdCourant()));
    }

    @GetMapping("/completude")
    @Operation(summary = "Taux de complétude de mon profil et éléments manquants")
    public ResponseEntity<Completude> completude() {
        return ResponseEntity.ok(espaceService.completude(perimetre.membreIdCourant()));
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Changer ma photo d'identité")
    public ResponseEntity<MembreResponseDTO> changerPhoto(@RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.ok(membreService.changerPhoto(perimetre.membreIdCourant(), fichier));
    }

    @GetMapping("/photo")
    @Operation(summary = "Ma photo d'identité")
    public ResponseEntity<byte[]> photo() {
        Fichier f = membreService.photo(perimetre.membreIdCourant());
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), true);
    }

    @PatchMapping("/disponibilite")
    @Operation(summary = "Me déclarer disponible (ou non) pour les mobilisations")
    public ResponseEntity<MembreResponseDTO> disponibilite(@RequestBody Disponibilite dto) {
        return ResponseEntity.ok(membreService.changerDisponibilite(perimetre.membreIdCourant(), dto.isDisponible()));
    }

    @PostMapping("/telephone/code")
    @Operation(summary = "Recevoir un code SMS pour vérifier mon numéro de téléphone")
    public ResponseEntity<Map<String, Object>> envoyerCodeTelephone() {
        return ResponseEntity.ok(authAvanceService.envoyerCodeTelephone());
    }

    @PostMapping("/telephone/verifier")
    @Operation(summary = "Valider mon numéro de téléphone avec le code reçu")
    public ResponseEntity<Map<String, Object>> verifierTelephone(@Valid @RequestBody Code dto) {
        return ResponseEntity.ok(authAvanceService.verifierTelephone(dto));
    }

    @GetMapping("/carte")
    @Operation(summary = "Ma carte de membre numérique (avec le contenu signé du QR code)")
    public ResponseEntity<Carte> carte() {
        return ResponseEntity.ok(espaceService.carte(perimetre.membreIdCourant()));
    }

    @GetMapping("/cv")
    @Operation(summary = "Mon CV numérique (données)")
    public ResponseEntity<Cv> cv() {
        return ResponseEntity.ok(espaceService.cv(perimetre.membreIdCourant()));
    }

    @GetMapping("/cv/pdf")
    @Operation(summary = "Télécharger mon CV (PDF)")
    public ResponseEntity<byte[]> cvPdf() {
        return Telechargement.fichier(espaceService.cvPdf(perimetre.membreIdCourant()), "mon-cv.pdf", "application/pdf", false);
    }
}
