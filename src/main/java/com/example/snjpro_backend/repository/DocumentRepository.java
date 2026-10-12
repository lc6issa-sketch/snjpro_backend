package com.example.snjpro_backend.repository;

import com.example.snjpro_backend.entity.Document;
import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByMembreIsNullOrderByDateAjoutDesc();
    List<Document> findByMembreIdOrderByDateAjoutDesc(Long membreId);
    List<Document> findByMembreIsNullAndVisibiliteInOrderByDateAjoutDesc(Collection<VisibiliteDocument> visibilites);
    long countByDossierId(Long dossierId);
    boolean existsByMembreIdAndTypeDocument(Long membreId, TypeDocument type);
}
