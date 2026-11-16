package com.hrm.backend.repository;

import com.hrm.backend.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findByAdminAdminIdOrderByHolidayDateAsc(Long adminId);

    List<Holiday> findByAdminAdminIdAndHolidayDateBetweenOrderByHolidayDateAsc(
            Long adminId,
            LocalDate startDate,
            LocalDate endDate
    );
}
