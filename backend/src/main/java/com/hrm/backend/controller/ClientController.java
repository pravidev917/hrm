
package com.hrm.backend.controller;

import com.hrm.backend.dto.ClientResponseDTO;
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
    public ResponseEntity<ClientResponseDTO> createClient(
            @RequestBody Client client,
            Authentication authentication) {

        Admin admin = adminService.getAdminRecord(
                adminService.getAdminIdByEmail(authentication.getName())
        );

        client.setAdmin(admin);
        client.setStatus("ACTIVE");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ClientResponseDTO.from(clientService.createClient(client)));
    }

    // List all clients belonging to logged-in admin
    @GetMapping
    public ResponseEntity<List<ClientResponseDTO>> getClients(
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                clientService.getClientsByAdmin(adminId)
                        .stream()
                        .map(ClientResponseDTO::from)
                        .toList()
        );
    }

    // Get client profile
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientResponseDTO> getClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                ClientResponseDTO.from(clientService.getClientById(clientId, adminId))
        );
    }

    // Update client
    @PutMapping("/{clientId}")
    public ResponseEntity<ClientResponseDTO> updateClient(
            @PathVariable Long clientId,
            @RequestBody Client client,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                ClientResponseDTO.from(clientService.updateClient(clientId, adminId, client))
        );
    }

    // Activate client
    @PatchMapping("/{clientId}/activate")
    public ResponseEntity<ClientResponseDTO> activateClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                ClientResponseDTO.from(clientService.activateClient(clientId, adminId))
        );
    }

    // Deactivate client
    @PatchMapping("/{clientId}/deactivate")
    public ResponseEntity<ClientResponseDTO> deactivateClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        return ResponseEntity.ok(
                ClientResponseDTO.from(clientService.deactivateClient(clientId, adminId))
        );
    }
    // Delete client (only when no report refers to it — otherwise deactivate)
    @DeleteMapping("/{clientId}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Long clientId,
            Authentication authentication) {

        Long adminId = adminService.getAdminIdByEmail(
                authentication.getName()
        );

        clientService.deleteClient(clientId, adminId);

        return ResponseEntity.noContent().build();
    }

    // Search clients by name
@GetMapping("/search")
public ResponseEntity<List<ClientResponseDTO>> searchClients(
        @RequestParam String name,
        Authentication authentication) {

    Long adminId = adminService.getAdminIdByEmail(
            authentication.getName()
    );

    return ResponseEntity.ok(
            clientService.searchClients(adminId, name)
                    .stream()
                    .map(ClientResponseDTO::from)
                    .toList()
    );
}
}
