package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Localisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocalisationRepository extends JpaRepository<Localisation, Long> {
    Optional<Localisation> findByMembreId(Long membreId);
    List<Localisation> findByRegionIgnoreCase(String region);
    @Query("SELECT DISTINCT l.region FROM Localisation l WHERE l.region IS NOT NULL ORDER BY l.region")
    List<String> findAllDistinctRegions();
}
