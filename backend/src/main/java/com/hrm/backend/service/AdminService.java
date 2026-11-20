package com.hrm.backend.service;

import com.hrm.backend.dto.AdminCreateRequest;
import com.hrm.backend.dto.AdminUpdateRequest;
import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Company;
import com.hrm.backend.repository.AdminRepository;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.HolidayRepository;
import com.hrm.backend.repository.LeaveManagementRepository;
import com.hrm.backend.repository.NoteRepository;
import com.hrm.backend.repository.OfficeLocationRepository;
import com.hrm.backend.repository.WfhRequestRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminService {

    private final AdminRepository repository;
    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final HolidayRepository holidayRepository;
    private final NoteRepository noteRepository;
    private final LeaveManagementRepository leaveRepository;
    private final WfhRequestRepository wfhRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            AdminRepository repository,
            EmployeeRepository employeeRepository,
            ClientRepository clientRepository,
            HolidayRepository holidayRepository,
            NoteRepository noteRepository,
            LeaveManagementRepository leaveRepository,
            WfhRequestRepository wfhRepository,
            OfficeLocationRepository officeLocationRepository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.holidayRepository = holidayRepository;
        this.noteRepository = noteRepository;
        this.leaveRepository = leaveRepository;
        this.wfhRepository = wfhRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Administrators of the caller's company. Accounts of other companies are not
     * listed, so one company's team never shows up in another's console.
     */
    @Transactional(readOnly = true)
    public List<Admin> getAllAdmins(Long actorId) {
        return repository.findByCompanyCompanyIdOrderByAdminIdAsc(requireCompanyId(actorId));
    }

    /**
     * The stored row of the signed-in administrator.
     * Used internally where the id already came from the session, so no extra
     * company check is needed.
     */
    @Transactional(readOnly = true)
    public Admin getAdminRecord(Long adminId) {
        return repository.findById(adminId).orElseThrow(this::notFound);
    }

    /** Administrator details, restricted to the caller's company. */
    @Transactional(readOnly = true)
    public Admin getAdminById(Long actorId, Long id) {
        Admin admin = repository.findById(id).orElseThrow(this::notFound);
        requireSameCompany(actorId, admin);
        return admin;
    }

    /**
     * The company a signed-in administrator belongs to.
     *
     * This is the only source of tenant identity in the application: it is read
     * from the administrator row that owns the authenticated email, so a request
     * can never choose or forge a company.
     */
    @Transactional(readOnly = true)
    public Long requireCompanyId(Long adminId) {
        return repository.findCompanyIdByAdminId(adminId)
                .orElseThrow(this::notFound);
    }

    private void requireSameCompany(Long actorId, Admin target) {
        Long actorCompany = requireCompanyId(actorId);
        Long targetCompany = target.getCompany() == null ? null : target.getCompany().getCompanyId();
        if (targetCompany == null || !targetCompany.equals(actorCompany)) {
            // 404 rather than 403: another company's account must look non-existent.
            throw notFound();
        }
    }

    // Get admin ID using logged-in admin email
    @Transactional(readOnly = true)
    public Long getAdminIdByEmail(String email) {

        return repository.findByEmail(email)
                .orElseThrow(this::notFound)
                .getAdminId();
    }

    public Admin createAdmin(Admin admin) {
        return repository.save(admin);
    }

    /**
     * An administrator creates another administrator account.
     * Emails are shared with employees (both are sign-in identifiers), so the
     * uniqueness check covers both tables.
     */
    @Transactional
    public Admin createAdminAccount(Long actorId, AdminCreateRequest request) {

        Admin actor = repository.findById(actorId).orElseThrow(this::notFound);
        Company company = actor.getCompany();

        if (company == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your administrator account is not attached to a company"
            );
        }

        String email = normalizeEmail(request.getEmail());

        if (repository.existsByEmail(email) || employeeRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already used by another account"
            );
        }

        Admin admin = new Admin();
        admin.setAdminName(request.getAdminName().trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRoleAccessLevel("ADMIN");
        admin.setContactDetails(blankToNull(request.getContactDetails()));
        admin.setStatus("ACTIVE");
        // Company membership is backend-verified: it comes from the creator's row.
        admin.setCompany(company);

        return repository.save(admin);
    }

    /**
     * Edits an administrator account. The password is only touched when the
     * request carries one, and it is always hashed before it is stored.
     */
    @Transactional
    public Admin updateAdminAccount(Long actorId, Long adminId, AdminUpdateRequest request) {

        Admin target = repository.findByIdForUpdate(adminId)
                .orElseThrow(this::notFound);

        requireSameCompany(actorId, target);

        String email = normalizeEmail(request.getEmail());

        if (repository.existsByEmailAndAdminIdNot(email, adminId)
                || employeeRepository.existsByEmail(email)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already used by another account"
            );
        }

        target.setAdminName(request.getAdminName().trim());
        target.setEmail(email);
        target.setContactDetails(blankToNull(request.getContactDetails()));

        String password = request.getPassword();
        if (password != null && !password.isBlank()) {
            target.setPassword(passwordEncoder.encode(password));
        }

        return repository.save(target);
    }

    /**
     * Activates or deactivates an administrator.
     *
     * Deactivation is refused for the signed-in account itself and for the last
     * active administrator, and every administrator row is locked first so two
     * concurrent requests cannot both believe another active administrator remains.
     *
     * A deactivated administrator keeps their row and everything it owns, but the
     * JWT filter re-checks the status on every request, so their existing session
     * stops working immediately.
     */
    @Transactional
    public Admin changeAdminStatus(Long actorId, Long adminId, boolean activate) {

        Long companyId = requireCompanyId(actorId);
        List<Admin> locked = repository.findCompanyForUpdate(companyId);

        Admin target = locked.stream()
                .filter(admin -> admin.getAdminId().equals(adminId))
                .findFirst()
                .orElseThrow(this::notFound);

        if (activate) {

            if ("ACTIVE".equals(target.getStatus())) {
                return target;
            }

            target.setStatus("ACTIVE");
            return repository.save(target);
        }

        if (target.getAdminId().equals(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You cannot deactivate your own account"
            );
        }

        if (!"ACTIVE".equals(target.getStatus())) {
            return target;
        }

        if (countActive(locked) <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This is the last active administrator. Reactivate or add another administrator first."
            );
        }

        target.setStatus("INACTIVE");
        return repository.save(target);
    }

    /**
     * Hard-deletes an administrator account.
     *
     * Only an account that owns no records can be deleted: clients, employees,
     * holidays, notes and reviewed leave requests all reference the administrator
     * row, so removing an account that owns any of them would break a foreign key
     * or destroy audit history. Those accounts must be deactivated instead.
     * Self-deletion and deleting the last active administrator are refused under
     * the same row locks as deactivation.
     */
    @Transactional
    public void deleteAdminAccount(Long actorId, Long adminId) {

        Long companyId = requireCompanyId(actorId);
        List<Admin> locked = repository.findCompanyForUpdate(companyId);

        Admin target = locked.stream()
                .filter(admin -> admin.getAdminId().equals(adminId))
                .findFirst()
                .orElseThrow(this::notFound);

        if (target.getAdminId().equals(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You cannot delete your own account"
            );
        }

        if ("ACTIVE".equals(target.getStatus()) && countActive(locked) <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This is the last active administrator. Add another administrator first."
            );
        }

        // Clients, holidays and employee notes are company data now, so they no
        // longer pin an administrator account. Employee accounts still carry their
        // creator, and reviewed leave requests are an audit trail, so both block a
        // hard delete.
        long employees = employeeRepository.countByAdminAdminId(adminId);
        long reviewedLeaves = leaveRepository.countByApprovedByAdminId(adminId);
        long reviewedWfh = wfhRepository.countByReviewedByAdminId(adminId);
        long officeEdits = officeLocationRepository.countByUpdatedByAdminId(adminId);

        if (employees + reviewedLeaves + reviewedWfh + officeEdits > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This administrator still owns " + (employees + reviewedLeaves + reviewedWfh + officeEdits)
                            + " record(s) (" + employees + " employee accounts, "
                            + reviewedLeaves + " reviewed leave requests, "
                            + reviewedWfh + " WFH decisions, "
                            + officeEdits + " office settings changes)."
                            + " Deactivate the account instead so those records and the"
                            + " audit history are preserved."
            );
        }

        repository.delete(target);
    }

    private long countActive(List<Admin> admins) {
        return admins.stream()
                .filter(admin -> "ACTIVE".equals(admin.getStatus()))
                .count();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrator not found");
    }
}
