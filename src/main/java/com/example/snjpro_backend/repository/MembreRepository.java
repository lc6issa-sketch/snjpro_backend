package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembreRepository extends JpaRepository<Membre, Long>, JpaSpecificationExecutor<Membre> {
    Optional<Membre> findByIdentifiantSnj(String identifiantSnj);
    Optional<Membre> findByUserId(Long userId);
    Optional<Membre> findFirstByNumeroTelephone(String numeroTelephone);
    Optional<Membre> findFirstByEmailIgnoreCase(String email);
    @Query("SELECT m FROM Membre m WHERE LOWER(m.nom) = LOWER(:nom) AND LOWER(m.prenom) = LOWER(:prenom) " +
            "AND ((:dateNaissance IS NULL AND m.dateNaissance IS NULL) OR m.dateNaissance = :dateNaissance)")
    List<Membre> findHomonymes(@Param("nom") String nom, @Param("prenom") String prenom,
                               @Param("dateNaissance") LocalDate dateNaissance);
    @Query("SELECT m FROM Membre m WHERE m.statut <> com.example.snj_pro_backend.enumeration.StatutMembreEnum.ARCHIVE")
    List<Membre> findAllNonArchives();
    boolean existsByIdentifiantSnj(String identifiantSnj);
    boolean existsByNumeroTelephone(String numeroTelephone);
    boolean existsByEmail(String email);
    Page<Membre> findByStatut(StatutMembreEnum statut, Pageable pageable);
    Page<Membre> findByCohorteId(Long cohorteId, Pageable pageable);
    @Query("SELECT m FROM Membre m WHERE " +
            "LOWER(m.nom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(m.prenom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(m.identifiantSnj) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Membre> rechercherMembres(@Param("query") String query, Pageable pageable);
    long countByStatut(StatutMembreEnum statut);
    long countBySexe(SexeEnum sexe);
    long countByCohorteId(Long cohorteId);
}
