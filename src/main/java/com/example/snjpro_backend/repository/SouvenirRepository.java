package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Souvenir;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SouvenirRepository extends JpaRepository<Souvenir, Long>, JpaSpecificationExecutor<Souvenir> {
    List<Souvenir> findByAuteurMembreIdOrderByDateCreationDesc(Long membreId);
}
