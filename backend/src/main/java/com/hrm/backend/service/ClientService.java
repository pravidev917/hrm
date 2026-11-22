package com.hrm.backend.service;

import com.hrm.backend.entity.Client;
import com.hrm.backend.repository.ClientRepository;
import com.hrm.backend.repository.DailyReportRepository;
import com.hrm.backend.repository.WeeklyReportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ClientService {

    /** Client ID: letters, digits and a few separators. */
    private static final Pattern CLIENT_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _./#-]{1,39}$");

    /** Contact numbers: digits with optional + ( ) - and spaces, at least 6 digits. */
    private static final Pattern CONTACT = Pattern.compile("^[0-9+()\\-\\s]{6,50}$");

    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ClientRepository repository;
    private final DailyReportRepository dailyReportRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final CompanyScopeService companyScope;

    public ClientService(
            ClientRepository repository,
            DailyReportRepository dailyReportRepository,
            WeeklyReportRepository weeklyReportRepository,
            CompanyScopeService companyScope) {

        this.repository = repository;
        this.dailyReportRepository = dailyReportRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.companyScope = companyScope;
    }

    // List clients belonging to an admin
    public List<Client> getClientsByAdmin(Long adminId) {
        return repository.findByCompanyOf(adminId);
    }

    /**
     * Active clients only, alphabetical. Used where a client must be selected
     * (filing a daily or weekly report); inactive clients are not valid targets.
     */
    public List<Client> getActiveClientsByAdmin(Long adminId) {
        return repository.findByCompanyOfAndStatus(adminId, "ACTIVE");
    }

    // Get client by ID
    public Client getClientById(Long clientId, Long adminId) {

        Client client = repository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        // Clients are shared by the company, so any administrator of the company
        // may read and edit them; another company's client is refused.
        if (!sameCompany(client, adminId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied: Client does not belong to this company");
        }

        return client;
    }

    private boolean sameCompany(Client client, Long adminId) {

        // Compare through a projection instead of client.getAdmin().getCompany():
        // those are lazy proxies, and this method is called from read paths whose
        // transaction has already closed (open-in-view is off), which raised
        // LazyInitializationException and a 500 on GET /api/admin/clients/{id}.
        Long clientCompany = repository.findCompanyIdByClientId(client.getClientId());
        Long adminCompany = companyScope.companyIdOf(adminId);

        return clientCompany != null && clientCompany.equals(adminCompany);
    }

    // Create client
    @Transactional
    public Client createClient(Client client) {

        if (client.getAdmin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client owner is required");
        }

        Long adminId = client.getAdmin().getAdminId();

        validate(client, adminId, null);

        client.setStatus("ACTIVE");
        return repository.save(client);
    }

    // Update client
    @Transactional
    public Client updateClient(Long clientId, Long adminId, Client request) {

        Client existing = getClientById(clientId, adminId);

        validate(request, adminId, clientId);

        apply(existing, request);

        return repository.save(existing);
    }

    // Activate client
    public Client activateClient(Long clientId, Long adminId) {

        Client client = getClientById(clientId, adminId);
        client.setStatus("ACTIVE");

        return repository.save(client);
    }

    // Deactivate client
    public Client deactivateClient(Long clientId, Long adminId) {

        Client client = getClientById(clientId, adminId);
        client.setStatus("INACTIVE");

        return repository.save(client);
    }

    /**
     * Delete a client that no report refers to yet.
     *
     * Reports hold a foreign key to the client, so deleting one that is in use
     * would either fail at the database or orphan history. Deactivating is the
     * supported way to retire a client that already has reports.
     */
    @Transactional
    public void deleteClient(Long clientId, Long adminId) {

        Client client = getClientById(clientId, adminId);

        long dailyReports = dailyReportRepository.countByClientClientId(clientId);
        long weeklyReports = weeklyReportRepository.countByClientClientId(clientId);

        if (dailyReports + weeklyReports > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format(
                            "This client is used by %d daily and %d weekly report(s). Deactivate it instead of deleting it so the reports keep their client.",
                            dailyReports, weeklyReports));
        }

        repository.delete(client);
    }

    // Search clients by name for a specific admin
    public List<Client> searchClients(Long adminId, String clientName) {

        String term = clientName == null ? "" : clientName.toLowerCase(Locale.ROOT);

        return repository.findByCompanyOf(adminId)
                .stream()
                .filter(client -> client.getClientName() != null
                        && client.getClientName().toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }

    /* ------------------------------- validation ------------------------------- */

    private void validate(Client request, Long adminId, Long existingClientId) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client details are required");
        }

        String clientCode = trimToNull(request.getClientCode());
        if (clientCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client ID is required");
        }
        if (!CLIENT_CODE.matcher(clientCode).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID may contain letters, digits, spaces and - _ . / # only (2–40 characters)");
        }

        String clientName = trimToNull(request.getClientName());
        if (clientName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client name is required");
        }
        checkLength("Client name", clientName, 150);
        checkLength("Vendor", request.getVendor(), 150);
        checkLength("BDM name", request.getBdmName(), 100);
        checkLength("Position", request.getPosition(), 150);
        checkLength("Client HR name", request.getHrName(), 100);
        checkLength("HR contact details", request.getHrContactDetails(), 150);

        String contactNumber = trimToNull(request.getContactNumber());
        if (contactNumber != null) {
            if (!CONTACT.matcher(contactNumber).matches() || digits(contactNumber) < 6) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Contact number must be at least 6 digits and may contain + ( ) - and spaces");
            }
        }

        if (request.getNumberOfPositions() != null) {
            int positions = request.getNumberOfPositions();
            if (positions < 0 || positions > 100000) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Number of positions must be between 0 and 100000");
            }
        }

        checkMoney("Salary offered", request.getSalaryOffered(), MAX_MONEY);
        checkMoney("Budget per candidate", request.getBudgetPerCandidate(), MAX_MONEY);
        checkPercent("Percentage per candidate", request.getPercentagePerCandidate());
        checkPercent("Tax", request.getTax());

        checkCount("Profiles submitted", request.getProfilesSubmitted());
        checkCount("Interviews conducted", request.getInterviewsConducted());
        checkCount("Selected candidates", request.getSelectedCandidates());

        // Unique per administrator, case-insensitive. Re-saving the same row is allowed.
        repository.findClientCodeInCompanyOf(adminId, clientCode)
                .filter(other -> existingClientId == null
                        || !other.getClientId().equals(existingClientId))
                .ifPresent(other -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Client ID " + clientCode + " is already used by another client");
                });
    }

    private void apply(Client target, Client request) {

        target.setClientCode(trimToNull(request.getClientCode()));
        target.setVendor(trimToNull(request.getVendor()));
        target.setBdmName(trimToNull(request.getBdmName()));
        target.setClientName(trimToNull(request.getClientName()));
        target.setPosition(trimToNull(request.getPosition()));
        target.setContactNumber(trimToNull(request.getContactNumber()));
        target.setHrName(trimToNull(request.getHrName()));
        target.setNumberOfPositions(request.getNumberOfPositions());
        target.setSalaryOffered(scale(request.getSalaryOffered()));
        target.setBudgetPerCandidate(scale(request.getBudgetPerCandidate()));
        target.setPercentagePerCandidate(scale(request.getPercentagePerCandidate()));
        target.setTax(scale(request.getTax()));
        target.setHrContactDetails(trimToNull(request.getHrContactDetails()));
        target.setJoiningDetails(trimToNull(request.getJoiningDetails()));

        // Legacy counters are not part of the client form any more: only written
        // when a request actually sends them, so editing a client cannot zero them.
        if (request.getProfilesSubmitted() != null) {
            target.setProfilesSubmitted(request.getProfilesSubmitted());
        }
        if (request.getInterviewsConducted() != null) {
            target.setInterviewsConducted(request.getInterviewsConducted());
        }
        if (request.getSelectedCandidates() != null) {
            target.setSelectedCandidates(request.getSelectedCandidates());
        }

        if (target.getProfilesSubmitted() == null) target.setProfilesSubmitted(0);
        if (target.getInterviewsConducted() == null) target.setInterviewsConducted(0);
        if (target.getSelectedCandidates() == null) target.setSelectedCandidates(0);
    }

    private void checkLength(String label, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, label + " must be at most " + max + " characters");
        }
    }

    private void checkMoney(String label, BigDecimal value, BigDecimal max) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " cannot be negative");
        }
        if (value.compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is too large");
        }
    }

    private void checkPercent(String label, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, label + " must be between 0 and 100");
        }
    }

    private void checkCount(String label, Integer value) {
        if (value != null && value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " cannot be negative");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private int digits(String value) {
        return (int) value.chars().filter(Character::isDigit).count();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
