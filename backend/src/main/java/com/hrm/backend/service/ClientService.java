
package com.hrm.backend.service;

import com.hrm.backend.entity.Client;
import com.hrm.backend.repository.ClientRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    // List clients belonging to an admin
    public List<Client> getClientsByAdmin(Long adminId) {
        return repository.findByAdminAdminId(adminId);
    }

    // Get client by ID
    public Client getClientById(Long clientId, Long adminId) {

        Client client = repository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        if (client.getAdmin() == null ||
                !client.getAdmin().getAdminId().equals(adminId)) {
            throw new RuntimeException("Access denied: Client does not belong to this admin");
        }

        return client;
    }

    // Create client
    public Client createClient(Client client) {
        return repository.save(client);
    }

    // Update client
    public Client updateClient(Long clientId, Long adminId, Client request) {

        Client existing = getClientById(clientId, adminId);

        if (request.getClientName() != null) {
            existing.setClientName(request.getClientName());
        }

        if (request.getHrName() != null) {
            existing.setHrName(request.getHrName());
        }

        if (request.getHrContactDetails() != null) {
            existing.setHrContactDetails(request.getHrContactDetails());
        }

        if (request.getJoiningDetails() != null) {
            existing.setJoiningDetails(request.getJoiningDetails());
        }

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
    // Search clients by name for a specific admin
public List<Client> searchClients(Long adminId, String clientName) {

    return repository.findByAdminAdminId(adminId)
            .stream()
            .filter(client -> client.getClientName()
                    .toLowerCase()
                    .contains(clientName.toLowerCase()))
            .toList();
}
}
