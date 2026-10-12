package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.ParcoursSNJ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcoursSNJRepository extends JpaRepository<ParcoursSNJ, Long> {
    List<ParcoursSNJ> findByMembreIdOrderByDateDebutDesc(Long membreId);
    List<ParcoursSNJ> findByType(String type);
}
