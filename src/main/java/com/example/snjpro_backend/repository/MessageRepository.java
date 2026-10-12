package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByDateEnvoiDesc(Long conversationId, Pageable pageable);
    Optional<Message> findFirstByConversationIdOrderByDateEnvoiDesc(Long conversationId);
    long countByConversationIdAndDateEnvoiAfterAndAuteurIdNot(Long conversationId, LocalDateTime date, Long auteurId);
    long countByConversationIdAndAuteurIdNot(Long conversationId, Long auteurId);
}
