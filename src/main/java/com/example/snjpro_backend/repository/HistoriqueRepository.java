package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Historique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoriqueRepository extends JpaRepository<Historique, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Historique> {
    Page<Historique> findByEntiteAndEntiteId(String entite, Long entiteId, Pageable pageable);
    Page<Historique> findByUtilisateur(String utilisateur, Pageable pageable);
}
