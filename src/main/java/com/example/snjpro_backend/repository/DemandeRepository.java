package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Demande;
import com.example.snjpro_backend.enumeration.Enumerations.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeRepository extends JpaRepository<Demande, Long> {
    List<Demande> findAllByOrderByDateCreationDesc();
    List<Demande> findByMembreIdOrderByDateCreationDesc(Long membreId);
    Optional<Demande> findByIdAndMembreId(Long id, Long membreId);
    long countByStatut(StatutDemande statut);

    @Query("SELECT MAX(d.numeroSuivi) FROM Demande d WHERE d.numeroSuivi LIKE CONCAT(:prefixe, '%')")
    String dernierNumero(String prefixe);
}
