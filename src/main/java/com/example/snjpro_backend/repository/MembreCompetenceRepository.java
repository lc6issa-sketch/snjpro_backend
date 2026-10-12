package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.MembreCompetence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembreCompetenceRepository extends JpaRepository<MembreCompetence, Long> {
    List<MembreCompetence> findByMembreId(Long membreId);
    List<MembreCompetence> findByIntituleContainingIgnoreCase(String intitule);
    void deleteByMembreId(Long membreId);
    @Query("SELECT DISTINCT c.intitule FROM MembreCompetence c ORDER BY c.intitule")
    List<String> findDistinctIntitules();
}
