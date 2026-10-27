package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.ImportSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportSessionRepository extends JpaRepository<ImportSession, Long> {
    Page<ImportSession> findAllByOrderByDateCreationDesc(Pageable pageable);
    Page<ImportSession> findByCohorteIdOrderByDateCreationDesc(Long cohorteId, Pageable pageable);
}
