package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.CompetenceReferentiel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompetenceReferentielRepository extends JpaRepository<CompetenceReferentiel, Long> {
    Optional<CompetenceReferentiel> findByNomIgnoreCase(String nom);
    boolean existsByNomIgnoreCase(String nom);
}
