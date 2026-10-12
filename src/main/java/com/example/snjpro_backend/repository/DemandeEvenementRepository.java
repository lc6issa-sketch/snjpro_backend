package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.DemandeEvenement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DemandeEvenementRepository extends JpaRepository<DemandeEvenement, Long> {
    List<DemandeEvenement> findByDemandeIdOrderByDateAsc(Long demandeId);
}
