package com.hrm.backend.service;

import com.hrm.backend.entity.MonthlyPerformance;
import com.hrm.backend.repository.MonthlyPerformanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonthlyPerformanceService {

    private final MonthlyPerformanceRepository repository;

    public MonthlyPerformanceService(MonthlyPerformanceRepository repository) {
        this.repository = repository;
    }

    public List<MonthlyPerformance> getAllMonthlyPerformance() {
        return repository.findAll();
    }

    public MonthlyPerformance getMonthlyPerformanceById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Monthly performance not found"));
    }

    public List<MonthlyPerformance> getPerformanceByEmployee(Long employeeId) {
        return repository.findByEmployeeId(employeeId);
    }

    public List<MonthlyPerformance> getPerformanceByMonth(
            Integer year, Integer month) {
        return repository.findByYearAndMonth(year, month);
    }

    public List<MonthlyPerformance> getPerformanceByYear(Integer year) {
        return repository.findByYear(year);
    }
}