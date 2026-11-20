package com.hrm.backend.service;

import com.hrm.backend.dto.AttendanceMarkRequest;
import com.hrm.backend.dto.AttendanceResponseDTO;
import com.hrm.backend.entity.Attendance;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.OfficeLocation;
import com.hrm.backend.entity.WfhRequest;
import com.hrm.backend.repository.AttendanceRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * Attendance with server-side location verification.
 *
 * Marking PRESENT is accepted in exactly two ways:
 *   1. an APPROVED work-from-home request covers the company-local today, or
 *   2. a fresh browser fix is within the configured office radius and accuracy.
 *
 * Everything the client sends is evidence, not a decision: the employee id comes
 * from the session, the company/office from the employee row, the date from the
 * server, the work mode and distance from the server's own calculation.
 * Pending, rejected, cancelled, expired and future WFH requests never unlock it.
 *
 * ABSENT needs no location. One record per employee and date is enforced by a
 * pessimistic employee lock plus the database unique constraint.
 */
@Service
public class AttendanceService {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    /** Allowance for a browser clock that is a few seconds ahead of the server. */
    private static final long MAX_FUTURE_SKEW_SECONDS = 30;

    private final AttendanceRepository repository;
    private final EmployeeRepository employeeRepository;
    private final OfficeLocationRepository officeRepository;
    private final WfhRequestRepository wfhRepository;
    private final EmployeeService employeeService;
    private final AdminService adminService;
    private final CompanyScopeService companyScope;
    private final CompanyTimeService companyTime;
    private final AuditLogService auditLog;
    private final double maxAccuracyMeters;
    private final long locationMaxAgeSeconds;

    public AttendanceService(
            AttendanceRepository repository,
            EmployeeRepository employeeRepository,
            OfficeLocationRepository officeRepository,
            WfhRequestRepository wfhRepository,
            EmployeeService employeeService,
            AdminService adminService,
            CompanyScopeService companyScope,
            CompanyTimeService companyTime,
            AuditLogService auditLog,
            @Value("${app.attendance.max-accuracy-meters:50}") double maxAccuracyMeters,
            @Value("${app.attendance.location-max-age-seconds:120}") long locationMaxAgeSeconds) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.officeRepository = officeRepository;
        this.wfhRepository = wfhRepository;
        this.employeeService = employeeService;
        this.adminService = adminService;
        this.companyScope = companyScope;
        this.companyTime = companyTime;
        this.auditLog = auditLog;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.locationMaxAgeSeconds = locationMaxAgeSeconds;
    }

    // ================= EMPLOYEE =================

    @Transactional
    public AttendanceResponseDTO markAttendance(
            Long employeeId,
            String status,
            AttendanceMarkRequest location) {

        Employee employee = lockedApprovedEmployee(employeeId);
        LocalDate today = companyTime.todayForEmployee(employee);

        if (repository
                .findByEmployeeEmployeeIdAndAttendanceDate(employeeId, today)
                .isPresent()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Attendance is already marked for today");
        }

        String normalized = validateStatus(status);

        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(today);

        applyStatus(attendance, employee, normalized, today, location);

        try {
            Attendance saved = repository.saveAndFlush(attendance);

            auditLog.employee(
                    employee,
                    "ATTENDANCE_MARKED",
                    "ATTENDANCE",
                    saved.getAttendanceId(),
                    "Marked " + saved.getStatus()
                            + (saved.getWorkMode() == null ? "" : " (" + saved.getWorkMode() + ")")
                            + " for " + saved.getAttendanceDate());

            return toDTO(saved);
        } catch (DataIntegrityViolationException ex) {
            // Protect against concurrent duplicate requests.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Attendance is already marked for today");
        }
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getEmployeeAttendance(Long employeeId) {

        requireApproved(employeeService.getEmployeeById(employeeId));

        return repository
                .findByEmployeeEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponseDTO getEmployeeAttendanceById(
            Long employeeId,
            Long attendanceId) {

        requireApproved(employeeService.getEmployeeById(employeeId));

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() -> notFound("Attendance not found"));

        if (!attendance.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return toDTO(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getEmployeeAttendanceByDateRange(
            Long employeeId,
            LocalDate startDate,
            LocalDate endDate) {

        requireApproved(employeeService.getEmployeeById(employeeId));
        validateDateRange(startDate, endDate);

        return repository
                .findByEmployeeEmployeeIdAndAttendanceDateBetween(
                        employeeId, startDate, endDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public AttendanceResponseDTO updateEmployeeAttendance(
            Long employeeId,
            Long attendanceId,
            String status,
            AttendanceMarkRequest location) {

        Employee employee = lockedApprovedEmployee(employeeId);
        LocalDate today = companyTime.todayForEmployee(employee);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() -> notFound("Attendance not found"));

        if (!attendance.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (!attendance.getAttendanceDate().equals(today)) {
            throw new IllegalArgumentException("Only today's attendance can be updated");
        }

        String normalized = validateStatus(status);

        if ("PRESENT".equals(normalized) && "PRESENT".equals(attendance.getStatus())) {
            // Idempotent: already present, nothing to re-verify or overwrite.
            return toDTO(attendance);
        }

        applyStatus(attendance, employee, normalized, today, location);

        Attendance saved = repository.saveAndFlush(attendance);

        auditLog.employee(
                employee,
                "ATTENDANCE_UPDATED",
                "ATTENDANCE",
                saved.getAttendanceId(),
                "Changed to " + saved.getStatus()
                        + (saved.getWorkMode() == null ? "" : " (" + saved.getWorkMode() + ")")
                        + " for " + saved.getAttendanceDate());

        return toDTO(saved);
    }

    // ================= ADMIN =================

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getAdminAttendance(Long adminId) {

        getActiveAdmin(adminId);

        return repository
                .findByEmployeeInCompanyOf(adminId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttendanceResponseDTO getAdminAttendanceById(
            Long adminId,
            Long attendanceId) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findById(attendanceId)
                .orElseThrow(() -> notFound("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        return toDTO(attendance);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getAdminAttendanceByDateRange(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate) {

        getActiveAdmin(adminId);
        validateDateRange(startDate, endDate);

        return repository
                .findByEmployeeInCompanyOfAndAttendanceDateBetween(
                        adminId, startDate, endDate)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Administrator correction of today's record.
     *
     * An administrator physically cannot supply the employee's location, so this
     * path records an explicit work mode instead. WFH may only be selected when an
     * approved WFH request covers the date; the server verifies that and writes the
     * correction to the audit trail. Client-provided distance/accuracy is never
     * accepted here.
     */
    @Transactional
    public AttendanceResponseDTO updateAdminAttendance(
            Long adminId,
            Long attendanceId,
            String status,
            String requestedWorkMode) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> notFound("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        Employee employee = attendance.getEmployee();
        LocalDate today = companyTime.todayForEmployee(employee);

        if (!attendance.getAttendanceDate().equals(today)) {
            throw new IllegalArgumentException("Only today's attendance can be updated");
        }

        String normalized = validateStatus(status);
        String previous = attendance.getStatus()
                + (attendance.getWorkMode() == null ? "" : "/" + attendance.getWorkMode());

        if ("ABSENT".equals(normalized)) {
            clearVerification(attendance);
            attendance.setStatus("ABSENT");
        } else {
            String mode = normalizeWorkMode(requestedWorkMode);
            WfhRequest wfh = approvedWfh(employee.getEmployeeId(), attendance.getAttendanceDate());

            if ("WFH".equals(mode) && wfh == null) {
                throw new IllegalArgumentException(
                        "WFH can only be recorded when an approved WFH request covers this date");
            }

            if (mode == null) {
                mode = wfh != null ? "WFH" : "OFFICE";
            }

            attendance.setStatus("PRESENT");
            attendance.setWorkMode(mode);
            attendance.setMarkedAt(LocalDateTime.now());

            if ("WFH".equals(mode)) {
                attendance.setWfhRequest(wfh);
                attendance.setDistanceMeters(null);
                attendance.setAccuracyMeters(null);
                attendance.setLocationCapturedAt(null);
            } else {
                // An administrative office correction is not a verified location reading.
                attendance.setWfhRequest(null);
                attendance.setDistanceMeters(null);
                attendance.setAccuracyMeters(null);
                attendance.setLocationCapturedAt(null);
            }
        }

        Attendance saved = repository.saveAndFlush(attendance);

        auditLog.admin(
                getActiveAdmin(adminId),
                "ATTENDANCE_CORRECTED",
                "ATTENDANCE",
                saved.getAttendanceId(),
                "Employee " + employee.getEmployeeName() + " (" + employee.getEmployeeId() + "): "
                        + previous + " -> " + saved.getStatus()
                        + (saved.getWorkMode() == null ? "" : "/" + saved.getWorkMode())
                        + " for " + saved.getAttendanceDate());

        return toDTO(saved);
    }

    @Transactional
    public void deleteAdminAttendance(Long adminId, Long attendanceId) {

        getActiveAdmin(adminId);

        Attendance attendance = repository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> notFound("Attendance not found"));

        validateAdminOwnership(adminId, attendance);

        auditLog.admin(
                getActiveAdmin(adminId),
                "ATTENDANCE_DELETED",
                "ATTENDANCE",
                attendance.getAttendanceId(),
                "Deleted " + attendance.getStatus()
                        + (attendance.getWorkMode() == null ? "" : "/" + attendance.getWorkMode())
                        + " record of employee " + attendance.getEmployee().getEmployeeName()
                        + " for " + attendance.getAttendanceDate());

        repository.delete(attendance);
    }

    // ================= VERIFICATION =================

    /**
     * Applies PRESENT or ABSENT to the entity, verifying location for office
     * attendance. Any failure is reported before the entity is persisted.
     */
    private void applyStatus(
            Attendance attendance,
            Employee employee,
            String status,
            LocalDate date,
            AttendanceMarkRequest request) {

        if ("ABSENT".equals(status)) {
            attendance.setMarkedAt(LocalDateTime.now());
            attendance.setStatus("ABSENT");
            clearVerification(attendance);
            return;
        }

        WfhRequest wfh = approvedWfh(employee.getEmployeeId(), date);
        OfficeVerification verification = wfh == null
                ? verifyOfficeLocation(employee, request)
                : null;

        attendance.setMarkedAt(LocalDateTime.now());
        attendance.setStatus("PRESENT");

        if (wfh != null) {
            // Approved WFH: no location is requested or required.
            attendance.setWorkMode("WFH");
            attendance.setWfhRequest(wfh);
            attendance.setDistanceMeters(null);
            attendance.setAccuracyMeters(null);
            attendance.setLocationCapturedAt(null);
            return;
        }

        attendance.setWorkMode("OFFICE");
        attendance.setWfhRequest(null);
        attendance.setDistanceMeters(verification.distanceMeters());
        attendance.setAccuracyMeters(verification.accuracyMeters());
        attendance.setLocationCapturedAt(verification.capturedAtUtc());
    }

    private OfficeVerification verifyOfficeLocation(
            Employee employee,
            AttendanceMarkRequest request) {

        Long companyId = CompanyTimeService.companyIdOf(employee);

        OfficeLocation office = companyId == null
                ? null
                : officeRepository.findByCompanyCompanyId(companyId).orElse(null);

        if (office == null || office.getLatitude() == null || office.getLongitude() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Office attendance is not configured yet. Ask your administrator to set the office location. "
                            + "If you have an approved WFH request for today, it will be used automatically.");
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Location is required to mark office attendance. Allow location access and try again.");
        }

        double latitude = requireFinite(request.latitude(), "Latitude");
        double longitude = requireFinite(request.longitude(), "Longitude");

        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(
                    "The location reading is invalid. Try again with a fresh location.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(
                    "The location reading is invalid. Try again with a fresh location.");
        }

        double accuracy = request.accuracyMeters() == null
                ? Double.NaN
                : request.accuracyMeters();

        if (!Double.isFinite(accuracy) || accuracy <= 0) {
            throw new IllegalArgumentException(
                    "The location accuracy is missing or invalid. Try again.");
        }
        if (accuracy > maxAccuracyMeters) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Your location is not accurate enough (about %.0f m). It must be within %.0f m. "
                            + "Move to an open area, wait a moment and try again.",
                    accuracy, maxAccuracyMeters));
        }

        Instant capturedAt = validateCaptureTime(request.capturedAtEpochMillis());

        double distance = distanceMeters(
                office.getLatitude().doubleValue(),
                office.getLongitude().doubleValue(),
                latitude,
                longitude);

        if (distance > office.getRadiusMeters()) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "You are about %.0f m from %s. Office attendance is allowed only within %d m. "
                            + "Move closer to the office and try again.",
                    distance, office.getOfficeName(), office.getRadiusMeters()));
        }

        return new OfficeVerification(
                BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(accuracy).setScale(2, RoundingMode.HALF_UP),
                LocalDateTime.ofInstant(capturedAt, ZoneOffset.UTC));
    }

    private Instant validateCaptureTime(Long epochMillis) {

        if (epochMillis == null || epochMillis <= 0) {
            throw new IllegalArgumentException(
                    "The location capture time is missing. Try again with a fresh location.");
        }

        Instant capturedAt;
        Instant now = companyTime.now();

        try {
            capturedAt = Instant.ofEpochMilli(epochMillis);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                    "The location capture time is invalid. Try again with a fresh location.");
        }

        if (capturedAt.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS))) {
            throw new IllegalArgumentException(
                    "The location capture time is in the future. Check your device clock and try again.");
        }

        long ageSeconds = Duration.between(capturedAt, now).getSeconds();

        if (ageSeconds > locationMaxAgeSeconds) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Your location reading is stale (about %d seconds old). Try again to capture a fresh location.",
                    Math.max(ageSeconds, 0)));
        }

        return capturedAt;
    }

    private WfhRequest approvedWfh(Long employeeId, LocalDate date) {

        return wfhRepository.findApprovedCovering(employeeId, date)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void clearVerification(Attendance attendance) {
        attendance.setWorkMode(null);
        attendance.setWfhRequest(null);
        attendance.setDistanceMeters(null);
        attendance.setAccuracyMeters(null);
        attendance.setLocationCapturedAt(null);
    }

    private static double requireFinite(Double value, String label) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " is required and must be a valid number");
        }
        return value;
    }

    /** Great-circle distance in metres (haversine). */
    static double distanceMeters(
            double officeLatitude,
            double officeLongitude,
            double latitude,
            double longitude) {

        double lat1 = Math.toRadians(officeLatitude);
        double lat2 = Math.toRadians(latitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(longitude - officeLongitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1d, Math.sqrt(a)));
    }

    // ================= VALIDATION =================

    private String validateStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Attendance status is required");
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("PRESENT") && !normalized.equals("ABSENT")) {
            throw new IllegalArgumentException(
                    "Attendance status must be PRESENT or ABSENT");
        }

        return normalized;
    }

    private String normalizeWorkMode(String workMode) {

        if (workMode == null || workMode.isBlank()) {
            return null;
        }

        String normalized = workMode.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("OFFICE") && !normalized.equals("WFH")) {
            throw new IllegalArgumentException("Work mode must be OFFICE or WFH");
        }

        return normalized;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    // ================= OWNERSHIP =================

    private Employee lockedApprovedEmployee(Long employeeId) {

        Employee employee = employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> notFound("Employee not found"));

        requireApproved(employee);
        return employee;
    }

    private void requireApproved(Employee employee) {
        if (!"APPROVED".equalsIgnoreCase(employee.getAccountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Employee account is not approved");
        }
    }

    private Admin getActiveAdmin(Long adminId) {
        return adminService.getAdminRecord(adminId);
    }

    private void validateAdminOwnership(Long adminId, Attendance attendance) {
        // Attendance is company data: any administrator of the employee's company
        // can correct it, nobody outside the company can.
        if (!companyScope.manages(adminId, attendance.getEmployee())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    // ================= DTO =================

    private AttendanceResponseDTO toDTO(Attendance attendance) {

        Employee employee = attendance.getEmployee();

        return new AttendanceResponseDTO(
                attendance.getAttendanceId(),
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                attendance.getAttendanceDate(),
                attendance.getStatus(),
                attendance.getWorkMode(),
                attendance.getDistanceMeters(),
                attendance.getAccuracyMeters(),
                attendance.getMarkedAt(),
                attendance.getWfhRequest() != null
                        ? attendance.getWfhRequest().getWfhId()
                        : null);
    }

    private record OfficeVerification(
            BigDecimal distanceMeters,
            BigDecimal accuracyMeters,
            LocalDateTime capturedAtUtc) {
    }
}
