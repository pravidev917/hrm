package com.hrm.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponseDTO {

    private Long attendanceId;

    private Long employeeId;

    private String employeeName;

    private LocalDate attendanceDate;

    private String status;
}