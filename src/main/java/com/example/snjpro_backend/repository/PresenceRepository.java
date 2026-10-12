package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Presence;
import com.example.snjpro_backend.enumeration.Enumerations.ModePresence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PresenceRepository extends JpaRepository<Presence, Long> {
    Page<Presence> findByActiviteIdOrderByDateHeureDesc(Long activiteId, Pageable pageable);
    List<Presence> findByActiviteIdOrderByDateHeureDesc(Long activiteId);
    List<Presence> findByMembreIdOrderByDateHeureDesc(Long membreId);
    Optional<Presence> findByActiviteIdAndMembreId(Long activiteId, Long membreId);
    long countByActiviteId(Long activiteId);
    long countByActiviteIdAndMode(Long activiteId, ModePresence mode);
    long countByActiviteIdAndDateHeureAfter(Long activiteId, LocalDateTime date);
    long countByMembreId(Long membreId);
}
