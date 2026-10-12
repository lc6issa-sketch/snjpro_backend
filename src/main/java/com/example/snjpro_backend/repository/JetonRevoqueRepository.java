package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.JetonRevoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface JetonRevoqueRepository extends JpaRepository<JetonRevoque, Long> {
    boolean existsByEmpreinte(String empreinte);

    @Modifying
    @Query("DELETE FROM JetonRevoque j WHERE j.dateExpiration < :date")
    int supprimerExpires(LocalDateTime date);
}
