
package com.hrm.backend.service;

import com.hrm.backend.dto.PerformanceRequestDTO;
import com.hrm.backend.dto.PerformanceResponseDTO;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Performance;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.PerformanceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PerformanceService {

    private static final int MAX_DESCRIPTION_LENGTH = 5000;

    private final PerformanceRepository performanceRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceService(
            PerformanceRepository performanceRepository,
            EmployeeRepository employeeRepository) {

        this.performanceRepository = performanceRepository;
        this.employeeRepository = employeeRepository;
    }

    // EMPLOYEE: Get own performance records
    @Transactional(readOnly = true)
    public List<PerformanceResponseDTO> getEmployeePerformance(
            Long employeeId) {

        return performanceRepository
                .findByEmployee_EmployeeIdOrderByPerformanceIdDesc(employeeId)
                .stream()
                .map(PerformanceResponseDTO::from)
                .toList();
    }

    // ADMIN: Get performance records of assigned employees
    @Transactional(readOnly = true)
    public List<PerformanceResponseDTO> getAdminPerformance(
            Long adminId) {

        return performanceRepository
                .findByEmployee_Admin_AdminIdOrderByPerformanceIdDesc(adminId)
                .stream()
                .map(PerformanceResponseDTO::from)
                .toList();
    }

    // EMPLOYEE: Create own performance record
    @Transactional
    public PerformanceResponseDTO createForEmployee(
            Long employeeId,
            PerformanceRequestDTO request) {

        Employee employee = findEmployee(employeeId);

        Performance performance = new Performance();
        performance.setEmployee(employee);

        applyRequest(performance, request);

        return PerformanceResponseDTO.from(
                performanceRepository.save(performance)
        );
    }

    // ADMIN: Create performance record for an assigned employee
    @Transactional
    public PerformanceResponseDTO createForAdmin(
            Long employeeId,
            Long adminId,
            PerformanceRequestDTO request) {

        Employee employee = findEmployee(employeeId);
        verifyAdminOwnership(employee, adminId);

        Performance performance = new Performance();
        performance.setEmployee(employee);

        applyRequest(performance, request);

        return PerformanceResponseDTO.from(
                performanceRepository.save(performance)
        );
    }

    // EMPLOYEE: Update own performance record
    @Transactional
    public PerformanceResponseDTO updateForEmployee(
            Long performanceId,
            Long employeeId,
            PerformanceRequestDTO request) {

        Performance performance = performanceRepository
                .findByPerformanceIdAndEmployee_EmployeeId(
                        performanceId,
                        employeeId
                )
                .orElseThrow(this::performanceNotFound);

        applyRequest(performance, request);

        return PerformanceResponseDTO.from(
                performanceRepository.save(performance)
        );
    }

    // ADMIN: Update performance belonging to assigned employee
    @Transactional
    public PerformanceResponseDTO updateForAdmin(
            Long performanceId,
            Long adminId,
            PerformanceRequestDTO request) {

        Performance performance = performanceRepository
                .findByPerformanceIdAndEmployee_Admin_AdminId(
                        performanceId,
                        adminId
                )
                .orElseThrow(this::performanceNotFound);

        applyRequest(performance, request);

        return PerformanceResponseDTO.from(
                performanceRepository.save(performance)
        );
    }

    // EMPLOYEE: Delete own performance record
    @Transactional
    public void deleteForEmployee(
            Long performanceId,
            Long employeeId) {

        Performance performance = performanceRepository
                .findByPerformanceIdAndEmployee_EmployeeId(
                        performanceId,
                        employeeId
                )
                .orElseThrow(this::performanceNotFound);

        performanceRepository.delete(performance);
    }

    // ADMIN: Delete performance belonging to assigned employee
    @Transactional
    public void deleteForAdmin(
            Long performanceId,
            Long adminId) {

        Performance performance = performanceRepository
                .findByPerformanceIdAndEmployee_Admin_AdminId(
                        performanceId,
                        adminId
                )
                .orElseThrow(this::performanceNotFound);

        performanceRepository.delete(performance);
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found"
                ));
    }

    private void verifyAdminOwnership(
            Employee employee,
            Long adminId) {

        if (employee.getAdmin() == null ||
                employee.getAdmin().getAdminId() == null ||
                !adminId.equals(employee.getAdmin().getAdminId())) {

            // Do not reveal employees belonging to another admin.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Employee not found"
            );
        }
    }

    private void applyRequest(
            Performance performance,
            PerformanceRequestDTO request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Performance request body is required"
            );
        }

        String daily = clean(request.dailyPerformance());
        String weekly = clean(request.weeklyPerformance());
        BigDecimal rating = request.overallRating();

        if (daily == null && weekly == null && rating == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one performance field is required"
            );
        }

        if (daily != null && daily.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Daily performance cannot exceed 5000 characters"
            );
        }

        if (weekly != null && weekly.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Weekly performance cannot exceed 5000 characters"
            );
        }

        if (rating != null) {
            if (rating.compareTo(BigDecimal.ZERO) < 0 ||
                    rating.compareTo(new BigDecimal("5.00")) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Overall rating must be between 0.00 and 5.00"
                );
            }

            if (rating.stripTrailingZeros().scale() > 2) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Overall rating cannot have more than 2 decimal places"
                );
            }
        }

        performance.setDailyPerformance(daily);
        performance.setWeeklyPerformance(weekly);
        performance.setOverallRating(rating);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private ResponseStatusException performanceNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Performance record not found"
        );
    }
}
