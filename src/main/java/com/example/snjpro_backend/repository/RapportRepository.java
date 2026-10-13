package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Rapport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RapportRepository extends JpaRepository<Rapport, Long> {
    List<Rapport> findAllByOrderByDateGenerationDesc();
}
