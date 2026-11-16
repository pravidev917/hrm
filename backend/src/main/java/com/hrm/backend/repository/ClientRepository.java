package com.hrm.backend.repository;

import com.hrm.backend.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByAdminAdminId(Long adminId);

    List<Client> findByAdminAdminIdAndStatusOrderByClientNameAsc(Long adminId, String status);

    List<Client> findByClientNameContainingIgnoreCase(String clientName);
}