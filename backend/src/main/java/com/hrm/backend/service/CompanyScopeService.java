package com.hrm.backend.service;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Company (tenant) guard.
 *
 * Administrators of one company share that company's records, so an employee is
 * manageable when it belongs to the caller's company — not when the caller happens
 * to have created it. The caller's company is always read from the stored
 * administrator row, never from a request parameter.
 *
 * Two states are distinguished deliberately:
 *   - another company  -> 403 (the record exists but is not yours)
 *   - no company/record -> 403 as well, so tenant membership can never be guessed
 */
@Service
public class CompanyScopeService {

    private final AdminRepository adminRepository;

    public CompanyScopeService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Transactional(readOnly = true)
    public Long companyIdOf(Long adminId) {
        return adminRepository.findCompanyIdByAdminId(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Administrator not found"));
    }

    /** True when the employee's company is the administrator's company. */
    @Transactional(readOnly = true)
    public boolean manages(Long adminId, Employee employee) {
        if (employee == null || employee.getAdmin() == null || employee.getAdmin().getCompany() == null) {
            return false;
        }
        Long employeeCompany = employee.getAdmin().getCompany().getCompanyId();
        return employeeCompany != null && employeeCompany.equals(companyIdOf(adminId));
    }

    /** Same as {@link #manages} but fails with 403 instead of returning a flag. */
    @Transactional(readOnly = true)
    public void requireManages(Long adminId, Employee employee) {
        if (!manages(adminId, employee)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: this record belongs to another company");
        }
    }
}
