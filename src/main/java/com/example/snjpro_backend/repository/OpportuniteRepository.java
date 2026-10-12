package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.StatutOpportunite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpportuniteRepository extends JpaRepository<Opportunite, Long> {
    List<Opportunite> findAllByOrderByDateCreationDesc();
    List<Opportunite> findByStatutOrderByDatePublicationDesc(StatutOpportunite statut);
}
