package com.hrm.backend.service;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.OfficeLocation;
import com.hrm.backend.repository.OfficeLocationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Single source of "today" for attendance and WFH decisions.
 *
 * The office timezone configured for the company wins; the application timezone
 * (APP_TIMEZONE) is the fallback for companies that have not configured an
 * office yet. The browser's date is never used for persistence.
 */
@Service
public class CompanyTimeService {

    private final OfficeLocationRepository officeRepository;
    private final ZoneId fallbackZone;

    public CompanyTimeService(
            OfficeLocationRepository officeRepository,
            @Value("${app.timezone:Asia/Kolkata}") String fallbackTimezone) {

        this.officeRepository = officeRepository;
        this.fallbackZone = parseZone(fallbackTimezone, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public ZoneId zoneForCompany(Long companyId) {

        if (companyId == null) {
            return fallbackZone;
        }

        return officeRepository.findByCompanyCompanyId(companyId)
                .map(OfficeLocation::getTimezone)
                .map(zone -> parseZone(zone, fallbackZone))
                .orElse(fallbackZone);
    }

    @Transactional(readOnly = true)
    public LocalDate todayForCompany(Long companyId) {
        return LocalDate.now(zoneForCompany(companyId));
    }

    @Transactional(readOnly = true)
    public LocalDate todayForEmployee(Employee employee) {
        return todayForCompany(companyIdOf(employee));
    }

    public Instant now() {
        return Instant.now();
    }

    public static Long companyIdOf(Employee employee) {
        if (employee == null || employee.getAdmin() == null || employee.getAdmin().getCompany() == null) {
            return null;
        }
        return employee.getAdmin().getCompany().getCompanyId();
    }

    private static ZoneId parseZone(String zone, ZoneId fallback) {
        if (zone == null || zone.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(zone.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
