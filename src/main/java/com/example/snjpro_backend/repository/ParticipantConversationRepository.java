package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.ParticipantConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantConversationRepository extends JpaRepository<ParticipantConversation, Long> {
    List<ParticipantConversation> findByConversationId(Long conversationId);
    Optional<ParticipantConversation> findByConversationIdAndUserId(Long conversationId, Long userId);
    List<ParticipantConversation> findByUserId(Long userId);
}
