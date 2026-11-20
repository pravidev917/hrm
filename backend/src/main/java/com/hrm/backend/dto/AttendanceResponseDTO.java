package com.hrm.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceResponseDTO {

    private Long attendanceId;

    private Long employeeId;

    private String employeeName;

    private LocalDate attendanceDate;

    private String status;

    /** OFFICE or WFH for verified PRESENT records; null for ABSENT/legacy rows. */
    private String workMode;

    /** Verified distance from the office in metres (office attendance only). */
    private BigDecimal distanceMeters;

    /** Browser-reported accuracy in metres (office attendance only). */
    private BigDecimal accuracyMeters;

    /** Server time at which the record was marked. */
    private LocalDateTime markedAt;

    /** Approved WFH request that justified a WFH record, when applicable. */
    private Long wfhRequestId;
}
