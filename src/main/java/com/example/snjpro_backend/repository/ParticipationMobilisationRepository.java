package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.ParticipationMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.ReponseMobilisation;
import com.example.snjpro_backend.enumeration.Enumerations.StatutMobilisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ParticipationMobilisationRepository extends JpaRepository<ParticipationMobilisation, Long> {
    List<ParticipationMobilisation> findByMobilisationId(Long mobilisationId);
    Page<ParticipationMobilisation> findByMobilisationIdOrderByDateReponseDesc(Long mobilisationId, Pageable pageable);
    Page<ParticipationMobilisation> findByMobilisationIdAndReponseOrderByDateReponseDesc(Long mobilisationId,
                                                                                       ReponseMobilisation reponse, Pageable pageable);
    List<ParticipationMobilisation> findByMobilisationIdAndReponse(Long mobilisationId, ReponseMobilisation reponse);
    long countByMobilisationId(Long mobilisationId);
    long countByMobilisationIdAndReponse(Long mobilisationId, ReponseMobilisation reponse);
    Optional<ParticipationMobilisation> findByMobilisationIdAndMembreId(Long mobilisationId, Long membreId);
    List<ParticipationMobilisation> findByMembreIdOrderByDateSollicitationDesc(Long membreId);
    List<ParticipationMobilisation> findByDateSollicitationAfter(LocalDateTime date);
    long countByMembreIdAndReponse(Long membreId, ReponseMobilisation reponse);

    /** Membres déjà mobilisés (disponibles) sur une opération en cours. */
    @Query("SELECT p.membre.id FROM ParticipationMobilisation p WHERE p.reponse = :reponse AND p.mobilisation.statut IN :statuts")
    Set<Long> membresMobilises(@Param("reponse") ReponseMobilisation reponse, @Param("statuts") List<StatutMobilisation> statuts);
}
