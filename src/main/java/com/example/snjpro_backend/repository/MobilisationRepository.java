package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Mobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MobilisationRepository extends JpaRepository<Mobilisation, Long> {
    List<Mobilisation> findAllByOrderByDateCreationDesc();
    List<Mobilisation> findByStatutAndEnvoiProgrammeLeLessThanEqual(StatutMobilisation statut, LocalDateTime date);
    List<Mobilisation> findByStatut(StatutMobilisation statut);
    long countByStatut(StatutMobilisation statut);
}
