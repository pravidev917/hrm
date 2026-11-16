package com.hrm.backend.controller;

import com.hrm.backend.dto.ClientOptionDTO;
import com.hrm.backend.service.ClientService;
import com.hrm.backend.service.EmployeeService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only client options for the signed-in employee.
 *
 * Daily reports are filed against a client, and the client belongs to the employee's
 * admin, so an employee needs the list to pick one. Scope is derived from the employee's
 * own record (admin_id), so an employee can only ever see their own organisation's clients.
 * No client management is exposed here: that stays admin-only.
 */
@RestController
@RequestMapping("/api/employee/clients")
public class EmployeeClientController {

    private final ClientService clientService;
    private final EmployeeService employeeService;

    public EmployeeClientController(
            ClientService clientService,
            EmployeeService employeeService) {

        this.clientService = clientService;
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<List<ClientOptionDTO>> getMyClients(
            Authentication authentication) {

        Long employeeId = employeeService.getEmployeeIdByEmail(
                authentication.getName());

        Long adminId = employeeService.getEmployeeById(employeeId)
                .getAdmin()
                .getAdminId();

        List<ClientOptionDTO> clients = clientService
                .getActiveClientsByAdmin(adminId)
                .stream()
                .map(client -> new ClientOptionDTO(
                        client.getClientId(),
                        client.getClientName(),
                        client.getHrName(),
                        client.getStatus()))
                .toList();

        return ResponseEntity.ok(clients);
    }
}
