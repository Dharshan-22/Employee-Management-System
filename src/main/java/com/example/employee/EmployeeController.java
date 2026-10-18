package com.example.employee;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository repository;
    private final ActivityLogRepository activityLogRepository;

    public EmployeeController(EmployeeRepository repository, ActivityLogRepository activityLogRepository) {
        this.repository = repository;
        this.activityLogRepository = activityLogRepository;
    }

    /**
     * Get all employees, optionally filtered by search keyword or department, and sorted.
     */
    @GetMapping
    public List<Employee> getAllEmployees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String order) {

        List<Employee> list;

        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim();
            try {
                int searchId = Integer.parseInt(q);
                Optional<Employee> byId = repository.findById(searchId);
                if (byId.isPresent()) {
                    list = new ArrayList<>(Collections.singletonList(byId.get()));
                } else {
                    list = repository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q);
                }
            } catch (NumberFormatException e) {
                list = repository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q);
            }
        } else {
            list = repository.findAll();
        }

        // Apply department filter if present
        if (department != null && !department.trim().isEmpty() && !"All Departments".equalsIgnoreCase(department.trim())) {
            String deptFilter = department.trim();
            list = list.stream()
                    .filter(e -> e.getDepartment() != null && e.getDepartment().equalsIgnoreCase(deptFilter))
                    .toList();
        }

        // Apply sorting
        boolean ascending = "asc".equalsIgnoreCase(order);
        Comparator<Employee> comparator;
        switch (sortBy.toLowerCase()) {
            case "name":
                comparator = Comparator.comparing(e -> e.getName() != null ? e.getName().toLowerCase() : "");
                break;
            case "department":
                comparator = Comparator.comparing(e -> e.getDepartment() != null ? e.getDepartment().toLowerCase() : "");
                break;
            case "salary":
                comparator = Comparator.comparingDouble(Employee::getSalary);
                break;
            case "joiningdate":
            case "date":
                comparator = Comparator.comparing(e -> e.getJoiningDate() != null ? e.getJoiningDate() : "");
                break;
            case "id":
            default:
                comparator = Comparator.comparingInt(Employee::getId);
                break;
        }

        if (!ascending) {
            comparator = comparator.reversed();
        }

        List<Employee> sortedList = new ArrayList<>(list);
        sortedList.sort(comparator);
        return sortedList;
    }

    /**
     * Get employee by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable int id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Add a new employee.
     */
    @PostMapping
    public ResponseEntity<?> addEmployee(@RequestBody Employee employee) {
        if (employee.getId() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee ID must be a positive number."));
        }
        if (repository.existsById(employee.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Employee ID " + employee.getId() + " already exists."));
        }
        if (employee.getName() == null || employee.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee name cannot be empty."));
        }
        if (employee.getDepartment() == null || employee.getDepartment().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Department cannot be empty."));
        }
        if (employee.getEmail() != null && !employee.getEmail().trim().isEmpty()) {
            if (repository.existsByEmailIgnoreCase(employee.getEmail().trim())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Email '" + employee.getEmail() + "' is already registered."));
            }
        }

        Employee saved = repository.save(employee);

        // Record audit activity log
        try {
            activityLogRepository.save(new ActivityLog(
                    "ADD",
                    saved.getId(),
                    "Added employee: " + saved.getName() + " (" + saved.getDepartment() + ")"
            ));
        } catch (Exception ignored) {}

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Update existing employee.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(
            @PathVariable int id,
            @RequestBody Employee updatedEmployee) {

        return repository.findById(id)
                .map(employee -> {
                    if (updatedEmployee.getName() != null && !updatedEmployee.getName().trim().isEmpty()) {
                        employee.setName(updatedEmployee.getName().trim());
                    }
                    if (updatedEmployee.getDepartment() != null && !updatedEmployee.getDepartment().trim().isEmpty()) {
                        employee.setDepartment(updatedEmployee.getDepartment().trim());
                    }
                    if (updatedEmployee.getJobTitle() != null) {
                        employee.setJobTitle(updatedEmployee.getJobTitle().trim());
                    }
                    if (updatedEmployee.getEmail() != null && !updatedEmployee.getEmail().trim().isEmpty()) {
                        if (repository.existsByEmailIgnoreCaseAndIdNot(updatedEmployee.getEmail().trim(), id)) {
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(Map.of("error", "Email is already registered to another employee."));
                        }
                        employee.setEmail(updatedEmployee.getEmail().trim());
                    }
                    if (updatedEmployee.getPhone() != null) {
                        employee.setPhone(updatedEmployee.getPhone().trim());
                    }
                    if (updatedEmployee.getSalary() > 0) {
                        employee.setSalary(updatedEmployee.getSalary());
                    }
                    if (updatedEmployee.getJoiningDate() != null) {
                        employee.setJoiningDate(updatedEmployee.getJoiningDate().trim());
                    }

                    Employee saved = repository.save(employee);

                    // Record audit activity log
                    try {
                        activityLogRepository.save(new ActivityLog(
                                "UPDATE",
                                saved.getId(),
                                "Updated details for: " + saved.getName()
                        ));
                    } catch (Exception ignored) {}

                    return ResponseEntity.ok(saved);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Employee not found with ID: " + id)));
    }

    /**
     * Delete employee by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable int id) {
        return repository.findById(id)
                .map(employee -> {
                    String empName = employee.getName();
                    repository.deleteById(id);

                    // Record audit activity log
                    try {
                        activityLogRepository.save(new ActivityLog(
                                "DELETE",
                                id,
                                "Deleted employee: " + empName
                        ));
                    } catch (Exception ignored) {}

                    return ResponseEntity.noContent().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Reports and Analytics Summary.
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Employee> all = repository.findAll();

        int totalCount = all.size();
        stats.put("totalEmployees", totalCount);

        Set<String> depts = new HashSet<>();
        double sumSalary = 0;
        double maxSalary = 0;
        double minSalary = Double.MAX_VALUE;
        Employee highestEmp = null;
        Employee lowestEmp = null;

        for (Employee e : all) {
            if (e.getDepartment() != null && !e.getDepartment().trim().isEmpty()) {
                depts.add(e.getDepartment().trim());
            }
            double sal = e.getSalary();
            sumSalary += sal;
            if (sal > maxSalary) {
                maxSalary = sal;
                highestEmp = e;
            }
            if (sal > 0 && sal < minSalary) {
                minSalary = sal;
                lowestEmp = e;
            }
        }

        stats.put("totalDepartments", depts.size());
        stats.put("averageSalary", totalCount > 0 ? (sumSalary / totalCount) : 0.0);
        stats.put("highestSalary", maxSalary);
        stats.put("highestPaidEmployee", highestEmp != null ? highestEmp.getName() : "N/A");
        stats.put("lowestSalary", minSalary == Double.MAX_VALUE ? 0.0 : minSalary);
        stats.put("lowestPaidEmployee", lowestEmp != null ? lowestEmp.getName() : "N/A");

        // Department-wise summary
        List<Object[]> rawDeptStats = repository.getDepartmentStats();
        List<Map<String, Object>> deptSummaries = new ArrayList<>();
        for (Object[] row : rawDeptStats) {
            Map<String, Object> deptItem = new LinkedHashMap<>();
            deptItem.put("department", row[0]);
            deptItem.put("count", row[1]);
            deptItem.put("avgSalary", row[2]);
            deptItem.put("minSalary", row[3]);
            deptItem.put("maxSalary", row[4]);
            deptSummaries.add(deptItem);
        }
        stats.put("departmentSummary", deptSummaries);

        return stats;
    }

    /**
     * Distinct departments list.
     */
    @GetMapping("/departments")
    public List<String> getDepartments() {
        return repository.findDistinctDepartments();
    }

    /**
     * Recent activity history.
     */
    @GetMapping("/activity-logs")
    public List<ActivityLog> getActivityLogs() {
        return activityLogRepository.findTop15ByOrderByTimestampDesc();
    }
}