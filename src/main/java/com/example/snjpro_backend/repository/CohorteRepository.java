package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Cohorte;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CohorteRepository extends JpaRepository<Cohorte, Long> {
    Optional<Cohorte> findByCode(String code);
    boolean existsByCode(String code);
    List<Cohorte> findByStatut(StatutCohorteEnum statut);
    List<Cohorte> findByNomContainingIgnoreCase(String nom);
    @Query("SELECT COUNT(m) FROM Membre m WHERE m.cohorte.id = :cohorteId")
    long countMembresInCohorte(Long cohorteId);
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT c.centre FROM Cohorte c WHERE c.centre IS NOT NULL ORDER BY c.centre")
    java.util.List<String> findDistinctCentres();
}
