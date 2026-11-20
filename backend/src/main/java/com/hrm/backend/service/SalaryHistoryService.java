package com.hrm.backend.service;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeSalaryHistory;
import com.hrm.backend.repository.EmployeeSalaryHistoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Month-effective salary history behind the salary x 3 revenue target.
 *
 * A change made in a later month is stored as a new row, so a report for an
 * earlier month keeps the salary that was effective then. A null row means the
 * salary was explicitly cleared and the month has no configured target.
 */
@Service
public class SalaryHistoryService {

    private final EmployeeSalaryHistoryRepository repository;
    private final CompanyTimeService companyTime;

    public SalaryHistoryService(
            EmployeeSalaryHistoryRepository repository,
            CompanyTimeService companyTime) {
        this.repository = repository;
        this.companyTime = companyTime;
    }

    @Transactional
    public void recordSalary(Employee employee, BigDecimal salary, boolean force) {

        if (employee == null) return;

        LocalDate effectiveMonth = firstOfMonth(
                companyTime.todayForEmployee(employee));

        EmployeeSalaryHistory entry = repository
                .findByEmployeeEmployeeIdAndEffectiveMonth(
                        employee.getEmployeeId(), effectiveMonth)
                .orElse(null);

        if (entry == null) {
            if (salary == null && !force) return;
            entry = new EmployeeSalaryHistory();
            entry.setEmployee(employee);
            entry.setEffectiveMonth(effectiveMonth);
        } else if (!force && sameMoney(entry.getSalary(), salary)) {
            return;
        }

        entry.setSalary(salary);
        repository.save(entry);
    }

    /** Salary effective in a month; null when the employee has no history or it was cleared. */
    @Transactional(readOnly = true)
    public BigDecimal salaryForMonth(Long employeeId, YearMonth month) {
        return repository.findByEmployeeEmployeeIdOrderByEffectiveMonthAsc(employeeId)
                .stream()
                .filter(row -> !row.getEffectiveMonth().isAfter(month.atDay(1)))
                .reduce((first, second) -> second)
                .map(EmployeeSalaryHistory::getSalary)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryHistory> history(Long employeeId) {
        return repository.findByEmployeeEmployeeIdOrderByEffectiveMonthAsc(employeeId);
    }

    public static LocalDate firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }
}
