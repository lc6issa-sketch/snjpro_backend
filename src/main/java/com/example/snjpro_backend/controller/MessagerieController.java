package com.example.snjpro_backend.controller;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.MessagerieDTOs.*;
import com.example.snjpro_backend.serviceInterface.MessagerieServiceInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messagerie")
@RequiredArgsConstructor
@Tag(name = "18. Messagerie", description = "Échanges entre membres, agents et directions régionales (F32 — espace communautaire)")
@SecurityRequirement(name = "bearerAuth")
public class MessagerieController {

    private final MessagerieServiceInterface service;

    @GetMapping("/conversations")
    @Operation(summary = "Mes conversations (dernier message, non lus, en ligne)")
    public ResponseEntity<List<ConversationDTO>> conversations(@RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.conversations(recherche));
    }

    @GetMapping("/non-lus")
    @Operation(summary = "Nombre total de messages non lus")
    public ResponseEntity<Map<String, Long>> nonLus() {
        return ResponseEntity.ok(Map.of("nonLus", service.nonLus()));
    }

    @PostMapping("/conversations")
    @Operation(summary = "Démarrer une conversation : avec un utilisateur, un membre, un service (REGIONAL / NATIONAL) ou un groupe",
            description = "Une conversation directe existante est réutilisée.")
    public ResponseEntity<ConversationDTO> creer(@RequestBody NouvelleConversation dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(dto));
    }

    @PostMapping("/conversations/cohorte")
    @Operation(summary = "Rejoindre le groupe de discussion de ma cohorte (membre)")
    public ResponseEntity<ConversationDTO> groupeCohorte() {
        return ResponseEntity.ok(service.groupeDeMaCohorte());
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Messages d'une conversation (du plus récent au plus ancien) — marque la conversation comme lue")
    public ResponseEntity<Page<MessageDTO>> messages(@PathVariable Long id, @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(service.messages(id, pageable));
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Envoyer un message")
    public ResponseEntity<MessageDTO> envoyer(@PathVariable Long id, @Valid @RequestBody NouveauMessage dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.envoyer(id, dto));
    }

    @PostMapping(value = "/conversations/{id}/pieces-jointes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envoyer une pièce jointe (avec un texte facultatif)")
    public ResponseEntity<MessageDTO> pieceJointe(@PathVariable Long id, @RequestParam("fichier") MultipartFile fichier,
                                                  @RequestParam(required = false) String contenu) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.envoyerPieceJointe(id, fichier, contenu));
    }

    @GetMapping("/messages/{messageId}/piece-jointe")
    @Operation(summary = "Télécharger la pièce jointe d'un message")
    public ResponseEntity<byte[]> telechargerPieceJointe(@PathVariable Long messageId) {
        Fichier f = service.pieceJointe(messageId);
        return Telechargement.fichier(f.contenu(), f.nom(), f.typeMime(), false);
    }

    @GetMapping("/contacts")
    @Operation(summary = "Contacts : services du SNJ et membres de ma cohorte (membre) ou collègues (agent)")
    public ResponseEntity<List<Contact>> contacts(@RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(service.contacts(recherche));
    }
}
