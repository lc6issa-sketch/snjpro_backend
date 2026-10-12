package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.NotificationDestinataire;
import com.example.snjpro_backend.enumeration.Enumerations.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface NotificationDestinataireRepository extends JpaRepository<NotificationDestinataire, Long> {

    @Query("SELECT d FROM NotificationDestinataire d JOIN FETCH d.notification n WHERE d.membre.id = :membreId " +
            "AND (:nonLues = false OR d.lu = false) AND (:type IS NULL OR n.type = :type) ORDER BY n.dateEnvoi DESC")
    Page<NotificationDestinataire> pourMembre(@Param("membreId") Long membreId, @Param("nonLues") boolean nonLues,
                                              @Param("type") TypeNotification type, Pageable pageable);

    long countByMembreIdAndLuFalse(Long membreId);
    long countByNotificationId(Long notificationId);
    long countByNotificationIdAndLuTrue(Long notificationId);
    Optional<NotificationDestinataire> findByIdAndMembreId(Long id, Long membreId);

    @Modifying
    @Query("UPDATE NotificationDestinataire d SET d.lu = true, d.dateLecture = :date WHERE d.membre.id = :membreId AND d.lu = false")
    int toutMarquerLu(@Param("membreId") Long membreId, @Param("date") LocalDateTime date);
}
