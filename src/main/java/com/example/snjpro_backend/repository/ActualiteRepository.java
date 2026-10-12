package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutActualite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActualiteRepository extends JpaRepository<Actualite, Long> {
    List<Actualite> findAllByOrderByDateCreationDesc();
    List<Actualite> findByStatutOrderByDatePublicationDesc(StatutActualite statut);
    List<Actualite> findByStatutAndDatePublicationLessThanEqual(StatutActualite statut, LocalDateTime date);
    long countByStatut(StatutActualite statut);
}
