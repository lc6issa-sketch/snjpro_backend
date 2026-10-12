package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {
    List<Candidature> findByOpportuniteIdOrderByScoreDesc(Long opportuniteId);
    List<Candidature> findByMembreIdOrderByDateCandidatureDesc(Long membreId);
    Optional<Candidature> findByOpportuniteIdAndMembreId(Long opportuniteId, Long membreId);
    long countByOpportuniteId(Long opportuniteId);
}
