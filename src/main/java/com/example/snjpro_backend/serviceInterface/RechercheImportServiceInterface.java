package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DoublonGroupeDTO;
import com.example.snjpro_backend.dto.ImportResultDTO;
import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.dto.MembreResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RechercheImportServiceInterface {
    Page<MembreResponseDTO> rechercheAvancee(MembreFilterDTO filter, Pageable pageable);
    ImportResultDTO importerFichier(MultipartFile fichier, Long cohorteId, String createdBy, boolean simulation);
    byte[] modeleImportCsv();
    List<DoublonGroupeDTO> detecterDoublons();
    MembreResponseDTO fusionnerMembres(Long membreIdPrincipal, Long membreIdDoublon);
}
