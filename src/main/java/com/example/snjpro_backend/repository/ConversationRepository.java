package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Conversation;
import com.example.snjpro_backend.enumeration.Enumerations.TypeConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByTypeAndCohorteId(TypeConversation type, Long cohorteId);

    @Query("SELECT c FROM Conversation c WHERE c.id IN (SELECT p.conversation.id FROM ParticipantConversation p WHERE p.userId = :userId) " +
            "ORDER BY COALESCE(c.dernierMessageLe, c.dateCreation) DESC")
    List<Conversation> duParticipant(@Param("userId") Long userId);
}
