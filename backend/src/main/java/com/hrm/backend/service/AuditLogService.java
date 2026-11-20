package com.hrm.backend.service;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.AuditLog;
import com.hrm.backend.entity.Company;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.repository.AuditLogRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only audit trail for company configuration and decisions.
 *
 * Details are short human-readable text. Employee coordinates are never written
 * here: attendance keeps only distance and accuracy evidence.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            Company company,
            String actorType,
            Long actorId,
            String actorName,
            String action,
            String entityType,
            Long entityId,
            String details) {

        if (company == null) {
            return;
        }

        AuditLog log = new AuditLog();
        log.setCompany(company);
        log.setActorType(actorType);
        log.setActorId(actorId);
        log.setActorName(actorName);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        repository.save(log);
    }

    public void admin(
            Admin admin,
            String action,
            String entityType,
            Long entityId,
            String details) {

        if (admin == null) {
            return;
        }

        record(
                admin.getCompany(),
                "ADMIN",
                admin.getAdminId(),
                admin.getAdminName(),
                action,
                entityType,
                entityId,
                details);
    }

    public void employee(
            Employee employee,
            String action,
            String entityType,
            Long entityId,
            String details) {

        if (employee == null || employee.getAdmin() == null) {
            return;
        }

        record(
                employee.getAdmin().getCompany(),
                "EMPLOYEE",
                employee.getEmployeeId(),
                employee.getEmployeeName(),
                action,
                entityType,
                entityId,
                details);
    }
}
