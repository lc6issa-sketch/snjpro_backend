package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.PermissionRole;
import com.example.snjpro_backend.enumeration.RoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRoleRepository extends JpaRepository<PermissionRole, Long> {
    List<PermissionRole> findByRole(RoleEnum role);
    Optional<PermissionRole> findByRoleAndModule(RoleEnum role, String module);
}
