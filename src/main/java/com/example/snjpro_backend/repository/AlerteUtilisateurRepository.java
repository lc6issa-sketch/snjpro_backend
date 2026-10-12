package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.AlerteUtilisateur;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlerteUtilisateurRepository extends JpaRepository<AlerteUtilisateur, Long> {
    Page<AlerteUtilisateur> findByUserIdOrderByDateCreationDesc(Long userId, Pageable pageable);
    Page<AlerteUtilisateur> findByUserIdAndLuFalseOrderByDateCreationDesc(Long userId, Pageable pageable);
    long countByUserIdAndLuFalse(Long userId);
    Optional<AlerteUtilisateur> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE AlerteUtilisateur a SET a.lu = true WHERE a.userId = :userId AND a.lu = false")
    int toutMarquerLu(@Param("userId") Long userId);
}
