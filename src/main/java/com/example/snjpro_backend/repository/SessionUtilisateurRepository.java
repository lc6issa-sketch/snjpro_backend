package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.SessionUtilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SessionUtilisateurRepository extends JpaRepository<SessionUtilisateur, Long> {
    List<SessionUtilisateur> findByUserIdAndActiveTrueAndDateExpirationAfterOrderByDateCreationDesc(Long userId, LocalDateTime date);
    Optional<SessionUtilisateur> findFirstByEmpreinte(String empreinte);
}
