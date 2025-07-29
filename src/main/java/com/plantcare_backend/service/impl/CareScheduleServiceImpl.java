package com.plantcare_backend.service.impl;

import com.plantcare_backend.dto.request.plantcare.CareScheduleSetupRequestDTO;
import com.plantcare_backend.dto.response.plantcare.CareScheduleResponseDTO;
import com.plantcare_backend.exception.ResourceNotFoundException;
import com.plantcare_backend.model.CareSchedule;
import com.plantcare_backend.model.CareType;
import com.plantcare_backend.model.UserPlants;
import com.plantcare_backend.repository.CareScheduleRepository;
import com.plantcare_backend.repository.CareTypeRepository;
import com.plantcare_backend.repository.UserPlantRepository;
import com.plantcare_backend.service.CareScheduleService;
import com.plantcare_backend.service.PlantCareNotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CareScheduleServiceImpl implements CareScheduleService {
    private final CareScheduleRepository careScheduleRepository;
    private final CareTypeRepository careTypeRepository;
    private final UserPlantRepository userPlantRepository;

    @Autowired
    private PlantCareNotificationService notificationService;

    @Override
    @Transactional
    public void setupCareSchedules(Long userPlantId, List<CareScheduleSetupRequestDTO> schedules) {
        UserPlants userPlant = userPlantRepository.findById(userPlantId)
                .orElseThrow(() -> new ResourceNotFoundException("User plant not found"));
        for (CareScheduleSetupRequestDTO dto : schedules) {
            CareType careType = careTypeRepository.findById(dto.getCareTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Care type not found"));
            Optional<CareSchedule> existing = careScheduleRepository
                    .findByUserPlant_UserPlantIdAndCareType_CareTypeId(userPlantId, dto.getCareTypeId());
            if (existing.isPresent()) {
                CareSchedule schedule = existing.get();
                schedule.setReminderEnabled(dto.getEnabled());
                schedule.setFrequencyDays(dto.getFrequencyDays());
                schedule.setReminderTime(dto.getReminderTime());
                schedule.setCustomMessage(dto.getCustomMessage());
                schedule.setStartDate(dto.getStartDate());

                // Tính toán next_care_date
                if (dto.getStartDate() != null) {
                    schedule.setNextCareDate(calculateNextCareDate(dto.getStartDate(), dto.getFrequencyDays()));
                }

                careScheduleRepository.save(schedule);

                // Kiểm tra và gửi reminder ngay nếu schedule đã đến hạn
                checkAndSendImmediateReminder(schedule);
            } else {
                CareSchedule schedule = CareSchedule.builder()
                        .userPlant(userPlant)
                        .careType(careType)
                        .reminderEnabled(dto.getEnabled())
                        .frequencyDays(dto.getFrequencyDays())
                        .reminderTime(dto.getReminderTime())
                        .customMessage(dto.getCustomMessage())
                        .startDate(dto.getStartDate())
                        .createdAt(new Timestamp(System.currentTimeMillis()))
                        .build();

                // Tính toán next_care_date cho schedule mới
                if (dto.getStartDate() != null) {
                    schedule.setNextCareDate(calculateNextCareDate(dto.getStartDate(), dto.getFrequencyDays()));
                }

                careScheduleRepository.save(schedule);

                // Kiểm tra và gửi reminder ngay nếu schedule đã đến hạn
                checkAndSendImmediateReminder(schedule);
            }
        }
    }

    private void checkAndSendImmediateReminder(CareSchedule schedule) {
        if (Boolean.TRUE.equals(schedule.getReminderEnabled()) &&
                schedule.getNextCareDate() != null &&
                schedule.getReminderTime() != null) {

            Date now = new Date();
            LocalTime currentTime = LocalTime.now();

            // Kiểm tra xem schedule có đến hạn ngay bây giờ không
            if (schedule.getNextCareDate().before(now) || schedule.getNextCareDate().equals(now)) {
                // Kiểm tra thời gian reminder có phù hợp không (trong khoảng ±30 phút)
                LocalTime reminderTime = schedule.getReminderTime();
                LocalTime timeWindowStart = reminderTime.minusMinutes(30);
                LocalTime timeWindowEnd = reminderTime.plusMinutes(30);

                if (currentTime.isAfter(timeWindowStart) && currentTime.isBefore(timeWindowEnd)) {
                    try {
                        notificationService.sendReminder(schedule);
                    } catch (Exception e) {
                        // Log lỗi nhưng không throw exception để không ảnh hưởng đến việc tạo schedule
                        System.err.println("Failed to send immediate reminder for schedule: " + schedule.getScheduleId()
                                + ", Error: " + e.getMessage());
                    }
                }
            }
        }
    }

    private Date calculateNextCareDate(Date startDate, Integer frequencyDays) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, frequencyDays);
        return cal.getTime();
    }

    @Override
    public List<CareScheduleResponseDTO> getCareSchedules(Long userPlantId) {
        List<CareSchedule> schedules = careScheduleRepository.findByUserPlant_UserPlantId(userPlantId);
        return schedules.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private CareScheduleResponseDTO toDTO(CareSchedule schedule) {
        CareScheduleResponseDTO dto = new CareScheduleResponseDTO();
        dto.setScheduleId(schedule.getScheduleId());
        dto.setCareTypeId(schedule.getCareType().getCareTypeId());
        dto.setCareTypeName(schedule.getCareType().getCareTypeName());
        dto.setEnabled(schedule.getReminderEnabled());
        dto.setFrequencyDays(schedule.getFrequencyDays());
        dto.setReminderTime(schedule.getReminderTime());
        dto.setCustomMessage(schedule.getCustomMessage());
        dto.setStartDate(schedule.getStartDate());
        dto.setLastCareDate(schedule.getLastCareDate());
        dto.setNextCareDate(schedule.getNextCareDate());
        return dto;
    }
}
