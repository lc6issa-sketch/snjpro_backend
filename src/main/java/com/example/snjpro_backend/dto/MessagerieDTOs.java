package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.TypeConversation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MessagerieDTOs {
    private MessagerieDTOs() { }

    @Data
    public static class NouvelleConversation {
        @Schema(description = "Conversation directe : identifiant de l'utilisateur destinataire")
        private Long destinataireUserId;
        @Schema(description = "Conversation directe avec un membre (via sa fiche)")
        private Long membreId;
        @Schema(description = "Écrire à un service : REGIONAL (direction régionale du membre) ou NATIONAL")
        private String service;
        @Schema(description = "Groupe : identifiants des participants")
        private List<Long> participantsUserIds = new ArrayList<>();
        private String titre;
        @Size(max = 4000)
        private String premierMessage;
    }

    @Data
    public static class NouveauMessage {
        @NotBlank(message = "Le message est vide")
        @Size(max = 4000)
        private String contenu;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConversationDTO {
        private Long id;
        private TypeConversation type;
        private String titre;
        private String sousTitre;
        private String initiales;
        private boolean enLigne;
        private String dernierMessage;
        private LocalDateTime dernierMessageLe;
        private long nonLus;
        private List<Long> participants;
        private Long membreId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageDTO {
        private Long id;
        private Long conversationId;
        private Long auteurId;
        private String auteur;
        private boolean deMoi;
        private String contenu;
        private Long pieceJointeTaille;
        private String pieceJointeNom;
        private String pieceJointeUrl;
        private LocalDateTime dateEnvoi;
        private boolean lu;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Contact {
        private String type;
        private Long userId;
        private Long membreId;
        private String nom;
        private String detail;
        private String service;
        private boolean enLigne;
        private String delaiReponse;
    }
}
