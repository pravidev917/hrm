
package com.hrm.backend.service;

import com.hrm.backend.dto.FollowupRequestDTO;
import com.hrm.backend.dto.FollowupResponseDTO;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.Followup;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.FollowupRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class FollowupService {

    private final FollowupRepository followupRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyScopeService companyScope;

    public FollowupService(
            FollowupRepository followupRepository,
            EmployeeRepository employeeRepository,
            CompanyScopeService companyScope) {

        this.followupRepository = followupRepository;
        this.employeeRepository = employeeRepository;
        this.companyScope = companyScope;
    }

    @Transactional(readOnly = true)
    public List<FollowupResponseDTO> getEmployeeFollowups(Long employeeId) {
        return followupRepository
                .findByEmployee_EmployeeIdOrderByFollowupIdDesc(employeeId)
                .stream()
                .map(FollowupResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FollowupResponseDTO> getAdminFollowups(Long adminId) {
        return followupRepository
                .findByEmployeeInCompanyOf(adminId)
                .stream()
                .map(FollowupResponseDTO::from)
                .toList();
    }

    @Transactional
    public FollowupResponseDTO createForEmployee(
            Long employeeId,
            FollowupRequestDTO request) {

        Employee employee = findEmployee(employeeId);

        Followup followup = new Followup();
        followup.setEmployee(employee);
        applyRequest(followup, request);

        return FollowupResponseDTO.from(
                followupRepository.save(followup)
        );
    }

    @Transactional
    public FollowupResponseDTO createForAdmin(
            Long employeeId,
            Long adminId,
            FollowupRequestDTO request) {

        Employee employee = findEmployee(employeeId);
        verifyAdminOwnership(employee, adminId);

        Followup followup = new Followup();
        followup.setEmployee(employee);
        applyRequest(followup, request);

        return FollowupResponseDTO.from(
                followupRepository.save(followup)
        );
    }

    @Transactional
    public FollowupResponseDTO updateForEmployee(
            Long followupId,
            Long employeeId,
            FollowupRequestDTO request) {

        Followup followup = followupRepository
                .findByFollowupIdAndEmployee_EmployeeId(
                        followupId, employeeId)
                .orElseThrow(this::notFound);

        applyRequest(followup, request);

        return FollowupResponseDTO.from(
                followupRepository.save(followup)
        );
    }

    @Transactional
    public FollowupResponseDTO updateForAdmin(
            Long followupId,
            Long adminId,
            FollowupRequestDTO request) {

        Followup followup = followupRepository
                .findByIdInCompanyOf(
                        followupId, adminId)
                .orElseThrow(this::notFound);

        applyRequest(followup, request);

        return FollowupResponseDTO.from(
                followupRepository.save(followup)
        );
    }

    @Transactional
    public void deleteForEmployee(Long followupId, Long employeeId) {
        Followup followup = followupRepository
                .findByFollowupIdAndEmployee_EmployeeId(
                        followupId, employeeId)
                .orElseThrow(this::notFound);

        followupRepository.delete(followup);
    }

    @Transactional
    public void deleteForAdmin(Long followupId, Long adminId) {
        Followup followup = followupRepository
                .findByIdInCompanyOf(
                        followupId, adminId)
                .orElseThrow(this::notFound);

        followupRepository.delete(followup);
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private void verifyAdminOwnership(Employee employee, Long adminId) {
        // Follow-ups are company data; another company's employee looks non-existent.
        if (!companyScope.manages(adminId, employee)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Employee not found");
        }
    }

    private void applyRequest(
            Followup followup,
            FollowupRequestDTO request) {

        if (request == null ||
                request.getCandidateName() == null ||
                request.getCandidateName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Candidate name is required");
        }

        String candidateName = request.getCandidateName().trim();

        if (candidateName.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Candidate name cannot exceed 100 characters");
        }

        String client = clean(request.getClient());
        String joiningStatus = clean(request.getJoiningStatus());

        if (client != null && client.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client cannot exceed 150 characters");
        }

        if (joiningStatus != null && joiningStatus.length() > 30) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Joining status cannot exceed 30 characters");
        }

        followup.setCandidateName(candidateName);
        followup.setClient(client);
        followup.setJoinDate(request.getJoinDate());
        followup.setJoiningStatus(joiningStatus);
        followup.setReason(clean(request.getReason()));
    }

    private String clean(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Follow-up not found");
    }
}
