package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.SituationProfessionnelle;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SituationProfessionnelleRepository extends JpaRepository<SituationProfessionnelle, Long> {
    Optional<SituationProfessionnelle> findByMembreId(Long membreId);
    List<SituationProfessionnelle> findByStatut(StatutProfessionnelEnum statut);
    long countByStatut(StatutProfessionnelEnum statut);
    @Query("SELECT DISTINCT s.profession FROM SituationProfessionnelle s WHERE s.profession IS NOT NULL ORDER BY s.profession")
    List<String> findDistinctProfessions();
}
