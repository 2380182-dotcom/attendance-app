package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.HolidayDTO;
import com.dawnbread.attendance.entity.CompanyHoliday;
import com.dawnbread.attendance.repository.AgentAbsenceRepository;
import com.dawnbread.attendance.repository.CompanyHolidayRepository;
import com.dawnbread.attendance.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Company holidays (V35), per tenant. Adding a holiday — or moving one to a
 * new date — removes any absences already saved for that date, because
 * nobody should be absent on a holiday. Deleting a holiday does NOT create
 * absences for that date after the fact.
 */
@Service
@Transactional
public class HolidayService {

    public static final int MAX_NAME = 100;

    @Autowired
    private CompanyHolidayRepository holidayRepository;
    @Autowired
    private AgentAbsenceRepository absenceRepository;
    @Autowired
    private Clock clock;

    @Transactional(readOnly = true)
    public List<HolidayDTO> list() {
        return holidayRepository.findByTenantIdOrderByHolidayDateDesc(TenantContext.getTenantId()).stream()
                .map(HolidayService::toDto).toList();
    }

    public HolidayDTO create(HolidayDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        validate(dto);
        if (holidayRepository.existsByTenantIdAndHolidayDate(tenantId, dto.holidayDate())) {
            throw new IllegalArgumentException("There is already a holiday on " + dto.holidayDate() + ".");
        }
        CompanyHoliday h = new CompanyHoliday();
        h.setTenantId(tenantId);
        h.setHolidayDate(dto.holidayDate());
        h.setName(dto.name().trim());
        h.setCreatedAt(LocalDateTime.now(clock));
        h = holidayRepository.save(h);
        absenceRepository.deleteForDate(tenantId, h.getHolidayDate());
        return toDto(h);
    }

    public HolidayDTO update(Long id, HolidayDTO dto) {
        Long tenantId = TenantContext.getTenantId();
        validate(dto);
        CompanyHoliday h = holidayRepository.findById(id)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new HolidayNotFoundException(id));
        holidayRepository.findByTenantIdAndHolidayDate(tenantId, dto.holidayDate())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("There is already a holiday on " + dto.holidayDate() + ".");
                });
        h.setHolidayDate(dto.holidayDate());
        h.setName(dto.name().trim());
        h.setUpdatedAt(LocalDateTime.now(clock));
        h = holidayRepository.save(h);
        absenceRepository.deleteForDate(tenantId, h.getHolidayDate());
        return toDto(h);
    }

    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        CompanyHoliday h = holidayRepository.findById(id)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new HolidayNotFoundException(id));
        holidayRepository.delete(h);
    }

    private static void validate(HolidayDTO dto) {
        if (dto == null || dto.holidayDate() == null) {
            throw new IllegalArgumentException("Please choose a date.");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Please enter the holiday name.");
        }
        if (dto.name().trim().length() > MAX_NAME) {
            throw new IllegalArgumentException("The name can be at most " + MAX_NAME + " characters.");
        }
    }

    private static HolidayDTO toDto(CompanyHoliday h) {
        return new HolidayDTO(h.getId(), h.getHolidayDate(), h.getName());
    }

    /** Unknown id, or another company's holiday — both answer 404 so nothing leaks across companies. */
    public static class HolidayNotFoundException extends RuntimeException {
        public HolidayNotFoundException(Long id) {
            super("Holiday not found: " + id);
        }
    }
}
