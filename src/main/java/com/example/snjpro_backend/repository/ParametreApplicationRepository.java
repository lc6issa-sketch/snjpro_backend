package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.ParametreApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametreApplicationRepository extends JpaRepository<ParametreApplication, String> {
}
