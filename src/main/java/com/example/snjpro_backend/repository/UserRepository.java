package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.User;
import com.example.snjpro_backend.enumeration.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByRole(RoleEnum role);
    List<User> findByActif(boolean actif);
}
