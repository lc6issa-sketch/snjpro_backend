package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Dossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Long> {
    boolean existsByNomIgnoreCase(String nom);
}
