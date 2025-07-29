package com.plantcare_backend.service;

import com.plantcare_backend.dto.response.plantsManager.PlantReportListResponseDTO;
import com.plantcare_backend.dto.request.plantsManager.PlantReportSearchRequestDTO;

public interface PlantReportService {
    PlantReportListResponseDTO getReportList(PlantReportSearchRequestDTO request);
}
