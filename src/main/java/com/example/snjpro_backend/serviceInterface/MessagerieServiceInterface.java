package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.MessagerieDTOs.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MessagerieServiceInterface {
    List<ConversationDTO> conversations(String recherche);
    long nonLus();
    ConversationDTO creer(NouvelleConversation dto);
    ConversationDTO groupeDeMaCohorte();
    Page<MessageDTO> messages(Long conversationId, Pageable pageable);
    MessageDTO envoyer(Long conversationId, NouveauMessage dto);
    MessageDTO envoyerPieceJointe(Long conversationId, MultipartFile fichier, String contenu);
    Fichier pieceJointe(Long messageId);
    List<Contact> contacts(String recherche, String portee, String region, Long cohorteId);
}
