package com.hrm.backend.service;

import com.hrm.backend.dto.OfficeLocationRequest;
import com.hrm.backend.dto.OfficeLocationResponseDTO;
import com.hrm.backend.dto.OfficeLocationSummaryDTO;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.OfficeLocation;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.OfficeLocationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;

/**
 * Company-level office settings.
 *
 * The office is stored once per company, so every existing and future
 * administrator of that company reads and updates the same configuration. The
 * company always comes from the authenticated administrator row.
 */
@Service
public class OfficeLocationService {

    private final OfficeLocationRepository repository;
    private final EmployeeRepository employeeRepository;
    private final AdminService adminService;
    private final AuditLogService auditLog;
    private final String defaultTimezone;

    public OfficeLocationService(
            OfficeLocationRepository repository,
            EmployeeRepository employeeRepository,
            AdminService adminService,
            AuditLogService auditLog,
            @Value("${app.timezone:Asia/Kolkata}") String defaultTimezone) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.adminService = adminService;
        this.auditLog = auditLog;
        this.defaultTimezone = defaultTimezone;
    }

    @Transactional(readOnly = true)
    public OfficeLocationResponseDTO getForAdmin(Long adminId) {

        Admin admin = activeAdmin(adminId);

        return repository.findByCompanyCompanyId(admin.getCompany().getCompanyId())
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public OfficeLocationSummaryDTO getForEmployee(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));

        Long companyId = CompanyTimeService.companyIdOf(employee);
        if (companyId == null) {
            return new OfficeLocationSummaryDTO(false, null, null, null);
        }

        return repository.findByCompanyCompanyId(companyId)
                .map(office -> new OfficeLocationSummaryDTO(
                        true,
                        office.getOfficeName(),
                        office.getRadiusMeters(),
                        office.getTimezone()))
                .orElseGet(() -> new OfficeLocationSummaryDTO(false, null, null, null));
    }

    @Transactional
    public OfficeLocationResponseDTO saveForAdmin(
            Long adminId,
            OfficeLocationRequest request) {

        Admin admin = activeAdmin(adminId);
        Company company = admin.getCompany();
        Long companyId = company.getCompanyId();

        String officeName = validateName(request.officeName());
        double latitude = requireFinite(request.latitude(), "Latitude");
        double longitude = requireFinite(request.longitude(), "Longitude");
        int radius = validateRadius(request.radiusMeters());
        String timezone = validateTimezone(request.timezone());

        OfficeLocation office = repository
                .findByCompanyCompanyIdForUpdate(companyId)
                .orElseGet(() -> {
                    OfficeLocation created = new OfficeLocation();
                    created.setCompany(company);
                    return created;
                });

        boolean isNew = office.getOfficeLocationId() == null;
        String before = isNew ? null : snapshot(office);

        office.setOfficeName(officeName);
        office.setLatitude(BigDecimal.valueOf(latitude).setScale(6, RoundingMode.HALF_UP));
        office.setLongitude(BigDecimal.valueOf(longitude).setScale(6, RoundingMode.HALF_UP));
        office.setRadiusMeters(radius);
        office.setTimezone(timezone);
        office.setUpdatedBy(admin);

        try {
            OfficeLocation saved = repository.saveAndFlush(office);

            auditLog.admin(
                    admin,
                    isNew ? "OFFICE_SETTINGS_CREATED" : "OFFICE_SETTINGS_UPDATED",
                    "OFFICE_LOCATION",
                    saved.getOfficeLocationId(),
                    isNew
                            ? "Created " + snapshot(saved)
                            : "Changed from [" + before + "] to [" + snapshot(saved) + "]");

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Two administrators saved the first configuration at the same time.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Office settings were just saved by another administrator. Reload the page and try again.");
        }
    }

    private Admin activeAdmin(Long adminId) {

        Admin admin = adminService.getAdminRecord(adminId);

        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admin account is not active");
        }

        if (admin.getCompany() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Administrator is not assigned to a company");
        }

        return admin;
    }

    private String validateName(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Office name is required");
        }

        String name = value.trim();
        if (name.length() > 150) {
            throw new IllegalArgumentException("Office name cannot exceed 150 characters");
        }
        return name;
    }

    private double requireFinite(Double value, String label) {

        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private int validateRadius(Integer value) {

        if (value == null) {
            throw new IllegalArgumentException("Allowed radius is required");
        }
        if (value < 10 || value > 10000) {
            throw new IllegalArgumentException("Radius must be between 10 and 10000 metres");
        }
        return value;
    }

    private String validateTimezone(String value) {

        String zone = value == null || value.isBlank()
                ? defaultTimezone
                : value.trim();

        try {
            ZoneId.of(zone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Timezone must be a valid IANA zone, for example Asia/Kolkata");
        }

        return zone;
    }

    private String snapshot(OfficeLocation office) {
        return "'" + office.getOfficeName() + "' at "
                + office.getLatitude().stripTrailingZeros().toPlainString() + ","
                + office.getLongitude().stripTrailingZeros().toPlainString()
                + " radius " + office.getRadiusMeters() + "m, " + office.getTimezone();
    }

    private OfficeLocationResponseDTO toResponse(OfficeLocation office) {

        return new OfficeLocationResponseDTO(
                office.getOfficeLocationId(),
                office.getOfficeName(),
                office.getLatitude(),
                office.getLongitude(),
                office.getRadiusMeters(),
                office.getTimezone(),
                office.getUpdatedBy() != null ? office.getUpdatedBy().getAdminId() : null,
                office.getUpdatedBy() != null ? office.getUpdatedBy().getAdminName() : null,
                office.getUpdatedAt());
    }
}
