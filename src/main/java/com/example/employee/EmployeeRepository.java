package com.example.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    List<Employee> findByNameContainingIgnoreCase(String name);

    List<Employee> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

    List<Employee> findByDepartmentIgnoreCase(String department);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, int id);

    @Query("SELECT DISTINCT e.department FROM Employee e WHERE e.department IS NOT NULL AND e.department != '' ORDER BY e.department ASC")
    List<String> findDistinctDepartments();

    @Query("SELECT e.department, COUNT(e), COALESCE(AVG(e.salary), 0.0), COALESCE(MIN(e.salary), 0.0), COALESCE(MAX(e.salary), 0.0) FROM Employee e GROUP BY e.department ORDER BY e.department ASC")
    List<Object[]> getDepartmentStats();
}