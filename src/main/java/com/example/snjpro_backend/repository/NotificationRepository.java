package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Notification;
import com.example.snjpro_backend.enumeration.Enumerations.StatutNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByOrderByDateCreationDesc(Pageable pageable);
    Page<Notification> findByEmetteurOrderByDateCreationDesc(String emetteur, Pageable pageable);
    List<Notification> findByStatutAndProgrammeLeLessThanEqual(StatutNotification statut, LocalDateTime date);
    List<Notification> findByDateEnvoiAfter(LocalDateTime date);
}
