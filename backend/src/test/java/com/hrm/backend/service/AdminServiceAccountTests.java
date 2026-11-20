package com.hrm.backend.service;

import com.hrm.backend.dto.AdminUpdateRequest;
import com.hrm.backend.dto.AdminCreateRequest;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account-status rules for administrators.
 *
 * These are the guarantees the UI cannot provide on its own: an administrator
 * cannot lock themselves out, the system can never lose its last active
 * administrator, and a delete can never silently take related records with it.
 * The service reads the active count from a locked snapshot of every
 * administrator row, so the same rules hold for concurrent requests.
 */
class AdminServiceAccountTests {

    private static final long COMPANY = 7L;

    private AdminRepository admins;
    private EmployeeRepository employees;
    private ClientRepository clients;
    private HolidayRepository holidays;
    private NoteRepository notes;
    private LeaveManagementRepository leaves;
    private PasswordEncoder passwordEncoder;
    private AdminService service;

    @BeforeEach
    void setUp() {
        admins = mock(AdminRepository.class);
        when(admins.findCompanyIdByAdminId(anyLong())).thenReturn(Optional.of(COMPANY));
        employees = mock(EmployeeRepository.class);
        clients = mock(ClientRepository.class);
        holidays = mock(HolidayRepository.class);
        notes = mock(NoteRepository.class);
        leaves = mock(LeaveManagementRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        service = new AdminService(
                admins, employees, clients, holidays, notes, leaves,
                mock(WfhRequestRepository.class), mock(OfficeLocationRepository.class),
                passwordEncoder);
    }

    private static Company company(long id) {
        Company company = new Company();
        company.setCompanyId(id);
        company.setCompanyName("Mahindra");
        return company;
    }

    private static Admin admin(long id, String name, String status) {
        Admin admin = new Admin();
        admin.setAdminId(id);
        admin.setAdminName(name);
        admin.setEmail(name.toLowerCase() + "@example.com");
        admin.setStatus(status);
        admin.setCompany(company(COMPANY));
        return admin;
    }

    private static int statusOf(ResponseStatusException exception) {
        return exception.getStatusCode().value();
    }

    @Test
    void cannotDeactivateYourOwnAccount() {

        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), admin(2, "Grace", "ACTIVE")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.changeAdminStatus(1L, 1L, false));

        assertEquals(409, statusOf(error));
        assertEquals("You cannot deactivate your own account", error.getReason());
        verify(admins, never()).save(any());
    }

    @Test
    void deactivatingAnotherAdministratorSetsInactive() {

        Admin target = admin(2, "Grace", "ACTIVE");
        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), target));
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Admin result = service.changeAdminStatus(1L, 2L, false);

        assertEquals("INACTIVE", result.getStatus());
    }

    @Test
    void cannotDeactivateTheLastActiveAdministrator() {

        // Defensive case: the caller is not one of the locked rows (for example an
        // account deactivated in another transaction), so only one active row remains.
        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(2, "Grace", "ACTIVE")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.changeAdminStatus(99L, 2L, false));

        assertEquals(409, statusOf(error));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(admins, never()).save(any());
    }

    @Test
    void reactivatingAnInactiveAdministratorSetsActive() {

        Admin target = admin(2, "Grace", "INACTIVE");
        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), target));
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("ACTIVE", service.changeAdminStatus(1L, 2L, true).getStatus());
    }

    @Test
    void cannotDeleteYourOwnAccount() {

        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), admin(2, "Grace", "ACTIVE")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAdminAccount(1L, 1L));

        assertEquals(409, statusOf(error));
        verify(admins, never()).delete(any());
    }

    @Test
    void cannotDeleteTheLastActiveAdministrator() {

        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(2, "Grace", "ACTIVE")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAdminAccount(99L, 2L));

        assertEquals(409, statusOf(error));
        verify(admins, never()).delete(any());
    }

    @Test
    void cannotDeleteAnAdministratorThatOwnsRecords() {

        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), admin(2, "Grace", "INACTIVE")));
        when(employees.countByAdminAdminId(2L)).thenReturn(3L);
        when(leaves.countByApprovedByAdminId(2L)).thenReturn(4L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteAdminAccount(1L, 2L));

        assertEquals(409, statusOf(error));
        assertEquals(true, error.getReason().contains("7 record(s)"));
        assertEquals(true, error.getReason().contains("Deactivate the account instead"));
        verify(admins, never()).delete(any());
    }

    @Test
    void deletesAnAdministratorThatOwnsNothing() {

        Admin target = admin(2, "Grace", "INACTIVE");
        when(admins.findCompanyForUpdate(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), target));

        service.deleteAdminAccount(1L, 2L);

        verify(admins).delete(target);
    }

    @Test
    void unknownAdministratorIsA404() {

        when(admins.findCompanyForUpdate(COMPANY)).thenReturn(List.of(admin(1, "Ada", "ACTIVE")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.changeAdminStatus(1L, 99L, false));

        assertEquals(404, statusOf(error));
    }

    @Test
    void updateRejectsAnEmailAlreadyUsedByAnotherAccount() {

        when(admins.findByIdForUpdate(2L)).thenReturn(Optional.of(admin(2, "Grace", "ACTIVE")));
        when(admins.existsByEmailAndAdminIdNot("ada@example.com", 2L)).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateAdminAccount(1L, 2L, updateRequest("Grace", "ada@example.com", null)));

        assertEquals(409, statusOf(error));
        verify(admins, never()).save(any());
    }

    @Test
    void updateKeepsTheStoredPasswordWhenNoneIsSupplied() {

        Admin target = admin(2, "Grace", "ACTIVE");
        target.setPassword("stored-hash");
        when(admins.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Admin result = service.updateAdminAccount(
                1L, 2L, updateRequest("Grace Hopper", "grace@example.com", null));

        assertEquals("Grace Hopper", result.getAdminName());
        assertEquals("stored-hash", result.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void updateHashesANewPasswordWhenOneIsSupplied() {

        Admin target = admin(2, "Grace", "ACTIVE");
        target.setPassword("stored-hash");
        when(admins.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Admin result = service.updateAdminAccount(
                1L, 2L, updateRequest("Grace", "grace@example.com", "new-password"));

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(admins).save(saved.capture());
        assertNotEquals("stored-hash", result.getPassword());
        assertEquals("new-hash", saved.getValue().getPassword());
    }

    @Test
    void aNewAdministratorJoinsTheCreatorsCompany() {

        Admin creator = admin(1, "Ada", "ACTIVE");
        when(admins.findById(1L)).thenReturn(Optional.of(creator));
        when(passwordEncoder.encode("Password@2026")).thenReturn("hash");
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCreateRequest request = new AdminCreateRequest();
        request.setAdminName("Grace Hopper");
        request.setEmail("grace.hopper@example.com");
        request.setPassword("Password@2026");

        Admin created = service.createAdminAccount(1L, request);

        // The company is copied from the creator's stored row, never from the request.
        assertEquals(COMPANY, created.getCompany().getCompanyId());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals("ADMIN", created.getRoleAccessLevel());
    }

    @Test
    void aNewAdministratorCannotBePlacedInAnotherCompany() {

        Admin creator = admin(1, "Ada", "ACTIVE");
        when(admins.findById(1L)).thenReturn(Optional.of(creator));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminCreateRequest request = new AdminCreateRequest();
        request.setAdminName("Grace Hopper");
        request.setEmail("grace.hopper@example.com");
        request.setPassword("Password@2026");

        Admin created = service.createAdminAccount(1L, request);

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(admins).save(saved.capture());
        assertEquals(COMPANY, saved.getValue().getCompany().getCompanyId());
    }

    @Test
    void theAdministratorListOnlyCoversTheCallersCompany() {

        when(admins.findByCompanyCompanyIdOrderByAdminIdAsc(COMPANY))
                .thenReturn(List.of(admin(1, "Ada", "ACTIVE"), admin(2, "Grace", "ACTIVE")));

        List<Admin> listed = service.getAllAdmins(1L);

        assertEquals(2, listed.size());
        verify(admins).findByCompanyCompanyIdOrderByAdminIdAsc(COMPANY);
        verify(admins, never()).findAll();
    }

    @Test
    void anotherCompanysAdministratorLooksNonExistent() {

        Admin other = admin(2, "Grace", "ACTIVE");
        other.setCompany(company(99L));
        when(admins.findById(2L)).thenReturn(Optional.of(other));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getAdminById(1L, 2L));

        assertEquals(404, statusOf(error));
    }

    @Test
    void statusChangesOnlyLockTheCallersCompany() {

        Admin target = admin(2, "Grace", "ACTIVE");
        when(admins.findCompanyForUpdate(COMPANY)).thenReturn(List.of(admin(1, "Ada", "ACTIVE"), target));
        when(admins.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeAdminStatus(1L, 2L, false);

        verify(admins).findCompanyForUpdate(COMPANY);
        verify(admins, never()).findAll();
    }

    private static AdminUpdateRequest updateRequest(String name, String email, String password) {
        AdminUpdateRequest request = new AdminUpdateRequest();
        request.setAdminName(name);
        request.setEmail(email);
        request.setContactDetails("98765 43210");
        request.setPassword(password);
        return request;
    }
}
