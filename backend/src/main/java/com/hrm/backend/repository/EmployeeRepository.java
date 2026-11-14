package com.hrm.backend.repository;

import com.hrm.backend.entity.Employee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByAdminAdminId(Long adminId);

    List<Employee> findByRole(String role);

    boolean existsByEmail(String email);

    Optional<Employee> findByEmail(String email);

    // Lock employee row while validating and saving leave applications.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :employeeId")
    Optional<Employee> findByIdForUpdate(
            @Param("employeeId") Long employeeId
    );
}