package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.PreferenceNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreferenceNotificationRepository extends JpaRepository<PreferenceNotification, Long> {
    List<PreferenceNotification> findByUserId(Long userId);
}
