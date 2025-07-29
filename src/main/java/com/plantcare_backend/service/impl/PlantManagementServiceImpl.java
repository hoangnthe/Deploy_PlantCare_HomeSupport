package com.plantcare_backend.service.impl;

import com.plantcare_backend.dto.response.plantsManager.*;
import com.plantcare_backend.dto.request.plantsManager.*;
import com.plantcare_backend.exception.InvalidDataException;
import com.plantcare_backend.exception.ResourceNotFoundException;
import com.plantcare_backend.model.*;
import com.plantcare_backend.repository.*;
import com.plantcare_backend.service.EmailService;
import com.plantcare_backend.service.PlantManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * created by TaHoang
 */
@Service
@RequiredArgsConstructor
public class PlantManagementServiceImpl implements PlantManagementService {
    @Autowired
    private final PlantRepository plantRepository;
    @Autowired
    private final PlantCategoryRepository plantCategoryRepository;
    @Autowired
    private final PlantReportRepository plantReportRepository;
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final EmailService emailService;
    @Autowired
    private final PlantReportLogRepository plantReportLogRepository;

    /**
     * Creates a new plant entry in the system by an admin or staff member.
     *
     * @param createPlantManagementRequestDTO the DTO containing all the plant
     *                                        details to be created.
     * @return the ID of the newly created plant.
     */
    @Override
    public Long createPlantByManager(CreatePlantManagementRequestDTO createPlantManagementRequestDTO, Long userId) {
        PlantCategory plantCategory = plantCategoryRepository
                .findById(Long.valueOf(createPlantManagementRequestDTO.getCategoryId()))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (plantRepository.existsByScientificNameIgnoreCase(createPlantManagementRequestDTO.getScientificName())) {
            throw new InvalidDataException("Plant with scientific name already exists: " + createPlantManagementRequestDTO.getScientificName());
        }
        if (plantRepository.existsByCommonNameIgnoreCase(createPlantManagementRequestDTO.getCommonName())) {
            throw new InvalidDataException("Plant with common name already exists: " + createPlantManagementRequestDTO.getCommonName());
        }
        Plants plants = new Plants();
        plants.setScientificName(createPlantManagementRequestDTO.getScientificName());
        plants.setCommonName(createPlantManagementRequestDTO.getCommonName());
        plants.setCategory(plantCategory);
        plants.setDescription(createPlantManagementRequestDTO.getDescription());
        plants.setCareInstructions(createPlantManagementRequestDTO.getCareInstructions());
        plants.setLightRequirement(
                Plants.LightRequirement.valueOf(createPlantManagementRequestDTO.getLightRequirement()));
        plants.setWaterRequirement(
                Plants.WaterRequirement.valueOf(createPlantManagementRequestDTO.getWaterRequirement()));
        plants.setCareDifficulty(Plants.CareDifficulty.valueOf(createPlantManagementRequestDTO.getCareDifficulty()));
        plants.setSuitableLocation(createPlantManagementRequestDTO.getSuitableLocation());
        plants.setCommonDiseases(createPlantManagementRequestDTO.getCommonDiseases());
        plants.setStatus(Plants.PlantStatus.ACTIVE);
        plants.setCreatedBy(userId);
        Plants saved = plantRepository.save(plants);
        if (createPlantManagementRequestDTO.getImageUrls() != null && !createPlantManagementRequestDTO.getImageUrls().isEmpty()) {
            List<PlantImage> images = new ArrayList<>();
            for (int i = 0; i < createPlantManagementRequestDTO.getImageUrls().size(); i++) {
                String url = createPlantManagementRequestDTO.getImageUrls().get(i);
                PlantImage image = PlantImage.builder()
                        .plant(saved) // Dùng saved thay vì plants
                        .imageUrl(url)
                        .isPrimary(i == 0) // Ảnh đầu tiên làm ảnh chính
                        .build();
                images.add(image);
            }
            saved.setImages(images);
            plantRepository.save(saved);
        }
        return saved.getId();
    }

    /**
     * Retrieves a paginated list of all plants in the system.
     *
     * @param page the page number to retrieve (0-based index).
     * @param size the number of records per page.
     * @return a {@link Page} of {@link PlantListResponseDTO} containing the plant
     * data.
     */
    @Override
    public Page<PlantListResponseDTO> getAllPlants(int page, int size) {
        Page<Plants> plantPage = plantRepository.findAll(PageRequest.of(page, size));
        return plantPage.map(this::toDTO);
    }

    /**
     * Searches for plants based on multiple filter criteria provided in the request
     * DTO.
     *
     * @param dto the {@link PlantSearchRequestDTO} containing filter parameters
     *            such as keyword,
     *            * category ID, light requirement, water requirement, care
     *            difficulty,
     *            * status, and pagination info (page and size).
     * @return a {@link Page} of {@link PlantListResponseDTO} containing the
     * filtered plant results.
     */
    @Override
    public Page<PlantListResponseDTO> searchPlants(PlantSearchRequestDTO dto) {
        Page<Plants> page = plantRepository.searchPlants(
                dto.getKeyword(),
                dto.getCategoryId(),
                dto.getLightRequirement() != null ? Plants.LightRequirement.valueOf(dto.getLightRequirement()) : null,
                dto.getWaterRequirement() != null ? Plants.WaterRequirement.valueOf(dto.getWaterRequirement()) : null,
                dto.getCareDifficulty() != null ? Plants.CareDifficulty.valueOf(dto.getCareDifficulty()) : null,
                dto.getStatus() != null ? Plants.PlantStatus.valueOf(dto.getStatus()) : null,
                PageRequest.of(dto.getPage(), dto.getSize()));
        return page.map(this::toDTO);
    }

    /**
     * Updates the details of an existing plant, including its information,
     * category,
     * status, and associated images based on the provided update request.
     *
     * @param plantId       the ID of the plant to be updated.
     * @param updateRequest the {@link UpdatePlantRequestDTO} containing new values
     *                      for the plant's fields,
     *                      such as name, description, care instructions, category,
     *                      status, and images.
     * @return the updated {@link PlantDetailResponseDTO} representing the plant's
     * latest data.
     */
    @Override
    public PlantDetailResponseDTO updatePlant(Long plantId, UpdatePlantRequestDTO updateRequest) {
        Plants plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + plantId));

        PlantCategory category = plantCategoryRepository.findById(updateRequest.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        plant.setScientificName(updateRequest.getScientificName());
        plant.setCommonName(updateRequest.getCommonName());
        plant.setCategory(category);
        plant.setDescription(updateRequest.getDescription());
        plant.setCareInstructions(updateRequest.getCareInstructions());
        plant.setLightRequirement(Plants.LightRequirement.valueOf(updateRequest.getLightRequirement()));
        plant.setWaterRequirement(Plants.WaterRequirement.valueOf(updateRequest.getWaterRequirement()));
        plant.setCareDifficulty(Plants.CareDifficulty.valueOf(updateRequest.getCareDifficulty()));
        plant.setSuitableLocation(updateRequest.getSuitableLocation());
        plant.setCommonDiseases(updateRequest.getCommonDiseases());
        plant.setStatus(Plants.PlantStatus.valueOf(updateRequest.getStatus()));

        // 4. Cập nhật ảnh - Logic linh hoạt
        if (updateRequest.getImageUpdates() != null && !updateRequest.getImageUpdates().isEmpty()) {
            // Xử lý update ảnh linh hoạt
            handleFlexibleImageUpdates(plant, updateRequest.getImageUpdates());
        } else if (updateRequest.getImageUrls() != null) {
            // Logic cũ - thay thế toàn bộ ảnh
            if (updateRequest.getImageUrls().isEmpty()) {
                plant.getImages().clear();
            } else {
                plant.getImages().clear();

                List<PlantImage> newImages = updateRequest.getImageUrls().stream()
                        .map(url -> PlantImage.builder()
                                .plant(plant)
                                .imageUrl(url)
                                .isPrimary(false)
                                .build())
                        .collect(Collectors.toList());

                if (!newImages.isEmpty()) {
                    newImages.get(0).setIsPrimary(true);
                }

                plant.getImages().addAll(newImages);
            }
        }
        // Nếu cả imageUpdates và imageUrls đều null, giữ nguyên ảnh cũ

        Plants updatedPlant = plantRepository.save(plant);
        return getPlantDetail(updatedPlant.getId());
    }

    /**
     * Retrieves detailed information of a plant by its ID, including scientific
     * name,
     * common name, description, care instructions, category name, status,
     * timestamps,
     * and associated images.
     *
     * @param plantId the ID of the plant to retrieve.
     * @return a {@link PlantDetailResponseDTO} containing detailed information
     * about the plant.
     */
    @Override
    public PlantDetailResponseDTO getPlantDetail(Long plantId) {
        Plants plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));

        PlantDetailResponseDTO dto = new PlantDetailResponseDTO();
        dto.setId(plant.getId());
        dto.setScientificName(plant.getScientificName());
        dto.setCommonName(plant.getCommonName());
        dto.setDescription(plant.getDescription());
        dto.setCareInstructions(plant.getCareInstructions());
        dto.setSuitableLocation(plant.getSuitableLocation());
        dto.setCommonDiseases(plant.getCommonDiseases());
        dto.setStatus(plant.getStatus() != null ? plant.getStatus().name() : null);
        dto.setCreatedAt(plant.getCreatedAt());
        dto.setUpdatedAt(plant.getUpdatedAt());
        dto.setCategoryName(plant.getCategory() != null ? plant.getCategory().getName() : null);

        List<String> imageUrls = new ArrayList<>();
        List<PlantImageDetailDTO> imageDetails = new ArrayList<>();

        if (plant.getImages() != null) {
            for (PlantImage img : plant.getImages()) {
                imageUrls.add(img.getImageUrl());

                PlantImageDetailDTO imageDetail = new PlantImageDetailDTO();
                imageDetail.setId(img.getId());
                imageDetail.setImageUrl(img.getImageUrl());
                imageDetail.setIsPrimary(img.getIsPrimary());
                imageDetail.setDescription(img.getDescription());
                imageDetails.add(imageDetail);
            }
        }
        dto.setImageUrls(imageUrls);
        dto.setImages(imageDetails);

        return dto;
    }

    /**
     * Lock or Unlock Plant, change status of plant.
     *
     * @param plantId the ID of the plant.
     * @param lock    the status of the plant.
     * @return
     */
    @Override
    public Plants.PlantStatus lockOrUnlockPlant(Long plantId, boolean lock) {
        Plants plant = plantRepository.findById(plantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found"));
        plant.setStatus(lock ? Plants.PlantStatus.INACTIVE : Plants.PlantStatus.ACTIVE);
        plantRepository.save(plant);
        return plant.getStatus();
    }

    /**
     * @param request
     * @return
     */
    @Override
    public PlantReportListResponseDTO getReportList(PlantReportSearchRequestDTO request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        PlantReport.ReportStatus status = null;
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            status = PlantReport.ReportStatus.valueOf(request.getStatus().toUpperCase());
        }

        Page<PlantReport> reportPage = plantReportRepository.findReportsWithFilters(
                status, request.getPlantName(), request.getReporterName(), pageable);

        PlantReportListResponseDTO response = new PlantReportListResponseDTO();
        response.setReports(reportPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
        response.setTotalElements((int) reportPage.getTotalElements());
        response.setTotalPages(reportPage.getTotalPages());
        response.setCurrentPage(request.getPage());
        response.setPageSize(request.getSize());

        return response;
    }

    @Override
    public void claimReport(Long reportId, Integer userId) {
        PlantReport plantReport = plantReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (plantReport.getClaimedBy() != null) {
            throw new IllegalArgumentException("Report đã được nhận sử lý bởi người khác!");
        }
        Users staff = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        plantReport.setClaimedBy(staff);
        plantReport.setClaimedAt(new Timestamp(System.currentTimeMillis()));
        plantReport.setStatus(PlantReport.ReportStatus.CLAIMED);
        plantReportRepository.save(plantReport);

        PlantReportLog log = new PlantReportLog();
        log.setReport(plantReport);
        log.setAction(PlantReportLog.Action.CLAIM);
        log.setUser(staff);
        log.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        plantReportLogRepository.save(log);
    }

    @Override
    public void handleReport(Long reportId, String status, String adminNotes, Integer userId) {
        PlantReport report = plantReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getClaimedBy() == null || report.getClaimedBy().getId() != userId.intValue()) {
            throw new IllegalStateException("Bạn không phải là người nhận xử lý report này!");
        }
        report.setStatus(PlantReport.ReportStatus.valueOf(status));
        report.setAdminNotes(adminNotes);
        report.setHandledBy(report.getClaimedBy());
        report.setHandledAt(new Timestamp(System.currentTimeMillis()));
        plantReportRepository.save(report);

        // Gửi email cho tất cả user đã report plant này
        List<PlantReport> allReports = plantReportRepository.findByPlant_Id(report.getPlant().getId());
        Set<Users> reporters = allReports.stream()
                .map(PlantReport::getReporter)
                .collect(Collectors.toSet());
        for (Users user : reporters) {
            String subject = "Báo cáo của bạn về cây " + report.getPlant().getCommonName() + " đã được xử lý";
            String content = "Chào " + user.getUsername() + ",\n\n"
                    + "Báo cáo của bạn về cây \"" + report.getPlant().getCommonName() + "\" đã được xử lý với kết quả: "
                    + report.getStatus().name() + ".\n"
                    + "Ghi chú từ admin/staff: " + report.getAdminNotes() + "\n\n"
                    + "Cảm ơn bạn đã đóng góp cho hệ thống PlantCare!";
            emailService.sendEmailAsync(user.getEmail(), subject, content);
        }

        // Ghi log
        PlantReportLog log = new PlantReportLog();
        log.setReport(report);
        log.setAction(PlantReportLog.Action.HANDLE);
        log.setUser(report.getClaimedBy());
        log.setNote(adminNotes);
        log.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        plantReportLogRepository.save(log);

        // Kiểm tra nếu không còn report nào ở trạng thái PENDING hoặc CLAIMED
        Long plantId = report.getPlant().getId();
        int pendingCount = plantReportRepository.countByPlantIdAndStatusIn(
                plantId,
                List.of(PlantReport.ReportStatus.PENDING, PlantReport.ReportStatus.CLAIMED));
        if (pendingCount == 0) {
            Plants plant = report.getPlant();
            if (plant.getStatus() == Plants.PlantStatus.INACTIVE) {
                plant.setStatus(Plants.PlantStatus.ACTIVE);
                plantRepository.save(plant);
            }
        }
    }

    @Override
    public PlantReportDetailResponseDTO getReportDetail(Long reportId) {
        PlantReport report = plantReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));

        PlantReportDetailResponseDTO dto = new PlantReportDetailResponseDTO();
        dto.setReportId(report.getReportId());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus().name());
        dto.setAdminNotes(report.getAdminNotes());
        dto.setCreatedAt(report.getCreatedAt());

        Plants plant = report.getPlant();
        dto.setPlantId(plant.getId());
        dto.setPlantName(plant.getCommonName());
        dto.setScientificName(plant.getScientificName());
        dto.setPlantDescription(plant.getDescription());
        dto.setPlantStatus(plant.getStatus() != null ? plant.getStatus().name() : null);
        dto.setCategoryName(plant.getCategory() != null ? plant.getCategory().getName() : null);

        List<String> imageUrls = new ArrayList<>();
        if (plant.getImages() != null) {
            imageUrls = plant.getImages().stream()
                    .map(PlantImage::getImageUrl)
                    .collect(Collectors.toList());
        }
        dto.setPlantImageUrls(imageUrls);

        Users reporter = report.getReporter();
        dto.setReporterId((long) reporter.getId());
        dto.setReporterName(reporter.getUsername());
        dto.setReporterEmail(reporter.getEmail());

        String reporterPhone = null;
        try {
            UserProfile userProfile = userRepository.findById(reporter.getId())
                    .map(Users::getUserProfile)
                    .orElse(null);
            if (userProfile != null) {
                reporterPhone = userProfile.getPhone();
            }
        } catch (Exception e) {
            System.err.println("Error getting reporter phone: " + e.getMessage());
        }
        dto.setReporterPhone(reporterPhone);

        if (report.getClaimedBy() != null) {
            Users claimedBy = report.getClaimedBy();
            dto.setClaimedById((long) claimedBy.getId());
            dto.setClaimedByName(claimedBy.getUsername());
            dto.setClaimedByEmail(claimedBy.getEmail());
            dto.setClaimedAt(report.getClaimedAt());
        }

        if (report.getHandledBy() != null) {
            Users handledBy = report.getHandledBy();
            dto.setHandledById((long) handledBy.getId());
            dto.setHandledByName(handledBy.getUsername());
            dto.setHandledByEmail(handledBy.getEmail());
            dto.setHandledAt(report.getHandledAt());
        }

        List<PlantReportLog> logs = plantReportLogRepository.findByReport_ReportId(reportId);
        List<PlantReportDetailResponseDTO.ReportLogDTO> logDTOs = logs.stream()
                .map(this::convertToLogDTO)
                .collect(Collectors.toList());
        dto.setReportLogs(logDTOs);

        return dto;
    }

    /**
     * Xử lý update ảnh linh hoạt - cho phép update từng ảnh cụ thể
     */
    private void handleFlexibleImageUpdates(Plants plant, List<PlantImageUpdateDTO> imageUpdates) {
        for (PlantImageUpdateDTO update : imageUpdates) {
            switch (update.getAction().toUpperCase()) {
                case "UPDATE":
                    // Update ảnh đã tồn tại
                    if (update.getImageId() != null) {
                        plant.getImages().stream()
                                .filter(img -> img.getId().equals(update.getImageId()))
                                .findFirst()
                                .ifPresent(img -> {
                                    img.setImageUrl(update.getImageUrl());
                                    if (update.getIsPrimary() != null) {
                                        img.setIsPrimary(update.getIsPrimary());
                                    }
                                });
                    }
                    break;

                case "DELETE":
                    // Xóa ảnh cụ thể
                    if (update.getImageId() != null) {
                        plant.getImages().removeIf(img -> img.getId().equals(update.getImageId()));
                    }
                    break;

                case "ADD":
                    // Thêm ảnh mới
                    PlantImage newImage = PlantImage.builder()
                            .plant(plant)
                            .imageUrl(update.getImageUrl())
                            .isPrimary(update.getIsPrimary() != null ? update.getIsPrimary() : false)
                            .build();
                    plant.getImages().add(newImage);
                    break;
            }
        }

        // Đảm bảo có ít nhất 1 ảnh primary
        boolean hasPrimary = plant.getImages().stream()
                .anyMatch(PlantImage::getIsPrimary);

        if (!hasPrimary && !plant.getImages().isEmpty()) {
            plant.getImages().get(0).setIsPrimary(true);
        }
    }

    private PlantListResponseDTO toDTO(Plants plant) {
        PlantListResponseDTO dto = new PlantListResponseDTO();
        dto.setId(plant.getId());
        dto.setCategoryName(plant.getCategory() != null ? plant.getCategory().getName() : null);
        dto.setScientificName(plant.getScientificName());
        dto.setCommonName(plant.getCommonName());
        dto.setDescription(plant.getDescription());

        // Lấy 1 ảnh đại diện với xử lý null safety tốt hơn
        String imageUrl = null;
        try {
            if (plant.getImages() != null && !plant.getImages().isEmpty()) {
                // Tìm ảnh primary trước
                PlantImage primary = plant.getImages().stream()
                        .filter(img -> img != null && Boolean.TRUE.equals(img.getIsPrimary()))
                        .findFirst()
                        .orElse(null);

                if (primary != null) {
                    imageUrl = primary.getImageUrl();
                } else {
                    // Nếu không có ảnh primary, lấy ảnh đầu tiên
                    PlantImage firstImage = plant.getImages().stream()
                            .filter(img -> img != null && img.getImageUrl() != null)
                            .findFirst()
                            .orElse(null);
                    if (firstImage != null) {
                        imageUrl = firstImage.getImageUrl();
                    }
                }
            }
        } catch (Exception e) {
            // Log lỗi nếu có vấn đề khi load images
            System.err.println("Error loading images for plant " + plant.getId() + ": " + e.getMessage());
        }

        dto.setImageUrl(imageUrl);
        dto.setStatus(plant.getStatus());
        dto.setCreatedAt(plant.getCreatedAt());
        return dto;
    }

    private PlantReportResponseDTO convertToDTO(PlantReport report) {
        PlantReportResponseDTO dto = new PlantReportResponseDTO();
        dto.setReportId(report.getReportId());
        dto.setPlantId(report.getPlant().getId());
        dto.setPlantName(report.getPlant().getCommonName());
        dto.setScientificName(report.getPlant().getScientificName());
        dto.setReporterId((long) report.getReporter().getId());
        dto.setReporterName(report.getReporter().getUsername());
        dto.setReporterEmail(report.getReporter().getEmail());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus().name());
        dto.setAdminNotes(report.getAdminNotes());
        dto.setCreatedAt(report.getCreatedAt());
        return dto;
    }

    private PlantReportDetailResponseDTO.ReportLogDTO convertToLogDTO(PlantReportLog log) {
        PlantReportDetailResponseDTO.ReportLogDTO dto = new PlantReportDetailResponseDTO.ReportLogDTO();
        dto.setLogId(log.getLogId());
        dto.setAction(log.getAction().name());
        dto.setUserName(log.getUser().getUsername());
        dto.setUserEmail(log.getUser().getEmail());
        dto.setNote(log.getNote());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
