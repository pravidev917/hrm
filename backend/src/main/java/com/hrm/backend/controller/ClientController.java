
package com.hrm.backend.controller;

import com.hrm.backend.entity.Admin;
import com.hrm.backend.entity.Client;
import com.hrm.backend.service.AdminService;
import com.hrm.backend.service.ClientService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/clients")
public class ClientController {

    private final ClientService clientService;
    private final AdminService adminService;

    public ClientController(
            ClientService clientService,
            AdminService adminService) {

        this.clientService = clientService;
        this.adminService = adminService;
    }

    // Create client
    @PostMapping
    public ResponseEntity<Client> createClient(
            @RequestBody Client client,
            Authentication authentication) {

        Admin admin = adminService.getAdminById(
                adminService.getAdminIdByEmail(authentication.getName())
        );

        client.setAdmin(admin);
        client.setStatus("ACTIVE");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clientService.createClient(client));
    }

    // List all clients belonging to logged-in admin
    @GetMapping
    public ResponseEntity<List<Client>> getClients(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.getClientsByAdmin(adminId)
        );
    }

    // Get client profile
    @GetMapping("/{clientId}")
    public ResponseEntity<Client> getClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.getClientById(clientId, adminId)
        );
    }

    // Update client
    @PutMapping("/{clientId}")
    public ResponseEntity<Client> updateClient(
            @PathVariable Long clientId,
            @RequestBody Client client,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.updateClient(clientId, adminId, client)
        );
    }

    // Activate client
    @PatchMapping("/{clientId}/activate")
    public ResponseEntity<Client> activateClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.activateClient(clientId, adminId)
        );
    }

    // Deactivate client
    @PatchMapping("/{clientId}/deactivate")
    public ResponseEntity<Client> deactivateClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.deactivateClient(clientId, adminId)
        );
    }
    // Search clients by name
@GetMapping("/search")
public ResponseEntity<List<Client>> searchClients(
        @RequestParam String name,
        Authentication authentication) {

    Long adminId = adminService.getAdminIdByEmail(
            authentication.getName()
    );

    return ResponseEntity.ok(
            clientService.searchClients(adminId, name)
    );
}
}
