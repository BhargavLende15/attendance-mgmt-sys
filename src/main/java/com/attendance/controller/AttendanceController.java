package com.attendance.controller;

import com.attendance.model.AttendanceRecord;
import com.attendance.model.CheckInRequest;
import com.attendance.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    /**
     * GET /attendance/status
     * Returns service health and basic stats
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = attendanceService.getServiceStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * POST /attendance/checkin
     * Simulates a user check-in
     */
    @PostMapping("/checkin")
    public ResponseEntity<?> checkIn(@RequestBody CheckInRequest request) {
        try {
            if (request.getEmployeeId() == null || request.getEmployeeId().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "employeeId is required"));
            }
            if (request.getEmployeeName() == null || request.getEmployeeName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "employeeName is required"));
            }
            if (request.getDepartment() == null || request.getDepartment().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "department is required"));
            }
            AttendanceRecord record = attendanceService.checkIn(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(record);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /attendance/checkout/{employeeId}
     */
    @PostMapping("/checkout/{employeeId}")
    public ResponseEntity<?> checkOut(@PathVariable String employeeId) {
        try {
            AttendanceRecord record = attendanceService.checkOut(employeeId);
            return ResponseEntity.ok(record);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /attendance/records
     * Returns all attendance records
     */
    @GetMapping("/records")
    public ResponseEntity<List<AttendanceRecord>> getAllRecords() {
        return ResponseEntity.ok(attendanceService.getAllRecords());
    }

    /**
     * GET /attendance/records/today
     */
    @GetMapping("/records/today")
    public ResponseEntity<List<AttendanceRecord>> getTodayRecords() {
        return ResponseEntity.ok(attendanceService.getTodayRecords());
    }

    /**
     * GET /attendance/records/{employeeId}
     */
    @GetMapping("/records/{employeeId}")
    public ResponseEntity<List<AttendanceRecord>> getEmployeeRecords(@PathVariable String employeeId) {
        return ResponseEntity.ok(attendanceService.getRecordsByEmployee(employeeId));
    }

    /**
     * GET /attendance/active
     */
    @GetMapping("/active")
    public ResponseEntity<List<AttendanceRecord>> getCurrentlyCheckedIn() {
        return ResponseEntity.ok(attendanceService.getCurrentlyCheckedIn());
    }

    /**
     * GET /attendance/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(attendanceService.getDashboardStats());
    }
}
