package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Activite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ActiviteRepository extends JpaRepository<Activite, Long> {
    List<Activite> findAllByOrderByDateDesc();
    List<Activite> findByDateLessThanEqualAndQrActifTrue(LocalDate date);
    List<Activite> findByDateLessThanEqual(LocalDate date);
}
