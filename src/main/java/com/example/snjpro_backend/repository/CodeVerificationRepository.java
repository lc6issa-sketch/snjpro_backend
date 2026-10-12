package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.CodeVerification;
import com.example.snjpro_backend.enumeration.Enumerations.TypeCodeVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeVerificationRepository extends JpaRepository<CodeVerification, Long> {
    Optional<CodeVerification> findFirstByTypeAndDestinationAndUtiliseFalseOrderByDateCreationDesc(
            TypeCodeVerification type, String destination);
    List<CodeVerification> findByTypeAndDestinationAndUtiliseFalse(TypeCodeVerification type, String destination);
    Optional<CodeVerification> findFirstByJetonAndTypeAndUtiliseFalse(String jeton, TypeCodeVerification type);
}
