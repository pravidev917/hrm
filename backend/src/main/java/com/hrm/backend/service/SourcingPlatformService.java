package com.hrm.backend.service;

import com.hrm.backend.dto.SourcingPlatformDTO;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.SourcingPlatform;
import com.hrm.backend.repository.SourcingPlatformRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Company-managed sourcing platform options.
 *
 * Names are stored as text on reports and candidate records, so renaming or
 * deactivating an option never rewrites historical records. Defaults are added
 * once per company when it has no options at all.
 */
@Service
public class SourcingPlatformService {

    /** The five platforms every form, table and chart offers, in display order. */
    private static final List<String> DEFAULTS = SourcingPlatforms.CANONICAL;

    private final SourcingPlatformRepository repository;
    private final CompanyScopeService companyScope;

    public SourcingPlatformService(
            SourcingPlatformRepository repository,
            CompanyScopeService companyScope) {
        this.repository = repository;
        this.companyScope = companyScope;
    }

    @Transactional
    public List<SourcingPlatformDTO> listForAdmin(Long adminId) {
        Long companyId = companyScope.companyIdOf(adminId);
        ensureDefaults(companyId);
        return repository.findByCompany(companyId).stream()
                .map(SourcingPlatformDTO::from)
                .toList();
    }

    @Transactional
    public List<SourcingPlatformDTO> listActiveForEmployee(Long employeeId, Long adminId) {
        Long companyId = companyScope.companyIdOf(adminId);
        ensureDefaults(companyId);
        return repository.findByCompany(companyId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(SourcingPlatformDTO::from)
                .toList();
    }

    @Transactional
    public SourcingPlatformDTO create(Long adminId, String name, Boolean active, Integer sortOrder) {
        Long companyId = companyScope.companyIdOf(adminId);
        String cleanName = validateName(name);
        repository.findByCompanyAndName(companyId, cleanName).ifPresent(existing -> {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "That sourcing platform already exists");
        });

        Company company = new Company();
        company.setCompanyId(companyId);

        SourcingPlatform platform = new SourcingPlatform();
        platform.setCompany(company);
        platform.setName(cleanName);
        platform.setActive(active == null || active);
        platform.setSortOrder(sortOrder == null ? 100 : sortOrder);

        return SourcingPlatformDTO.from(repository.save(platform));
    }

    @Transactional
    public SourcingPlatformDTO update(Long adminId, Long platformId, String name, Boolean active, Integer sortOrder) {
        Long companyId = companyScope.companyIdOf(adminId);
        SourcingPlatform platform = repository.findByIdInCompany(platformId, companyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Sourcing platform not found"));

        if (name != null && !name.isBlank()) {
            String cleanName = validateName(name);
            repository.findByCompanyAndName(companyId, cleanName)
                    .filter(other -> !other.getSourcingPlatformId().equals(platformId))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "That sourcing platform already exists");
                    });
            platform.setName(cleanName);
        }
        if (active != null) platform.setActive(active);
        if (sortOrder != null) platform.setSortOrder(sortOrder);

        return SourcingPlatformDTO.from(repository.save(platform));
    }

    /** The names currently available for new reports, oldest company options included. */
    @Transactional(readOnly = true)
    public List<String> activeNames(Long adminId) {
        Long companyId = companyScope.companyIdOf(adminId);
        return repository.findByCompany(companyId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(SourcingPlatform::getName)
                .toList();
    }

    private void ensureDefaults(Long companyId) {
        if (!repository.findByCompany(companyId).isEmpty()) return;

        Company company = new Company();
        company.setCompanyId(companyId);

        int order = 10;
        for (String name : DEFAULTS) {
            SourcingPlatform platform = new SourcingPlatform();
            platform.setCompany(company);
            platform.setName(name);
            platform.setActive(true);
            platform.setSortOrder(order);
            order += 10;
            repository.save(platform);
        }
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Platform name is required");
        }
        String clean = name.trim();
        if (clean.length() > 80) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Platform name cannot exceed 80 characters");
        }
        return clean;
    }
}
