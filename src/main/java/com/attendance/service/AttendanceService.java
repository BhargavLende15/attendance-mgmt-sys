package com.attendance.service;

import com.attendance.model.AttendanceRecord;
import com.attendance.model.CheckInRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    public AttendanceRecord checkIn(CheckInRequest request) {
        // Check if already checked in
        Optional<AttendanceRecord> existing = attendanceRepository
            .findTopByEmployeeIdOrderByCheckInTimeDesc(request.getEmployeeId());

        if (existing.isPresent() && existing.get().getStatus() == AttendanceRecord.AttendanceStatus.CHECKED_IN) {
            throw new RuntimeException("Employee " + request.getEmployeeId() + " is already checked in.");
        }

        AttendanceRecord record = new AttendanceRecord(
            request.getEmployeeId(),
            request.getEmployeeName(),
            request.getDepartment()
        );
        record.setNotes(request.getNotes());
        return attendanceRepository.save(record);
    }

    public AttendanceRecord checkOut(String employeeId) {
        Optional<AttendanceRecord> existing = attendanceRepository
            .findTopByEmployeeIdOrderByCheckInTimeDesc(employeeId);

        if (existing.isEmpty() || existing.get().getStatus() != AttendanceRecord.AttendanceStatus.CHECKED_IN) {
            throw new RuntimeException("Employee " + employeeId + " is not currently checked in.");
        }

        AttendanceRecord record = existing.get();
        record.setCheckOutTime(LocalDateTime.now());
        record.setStatus(AttendanceRecord.AttendanceStatus.CHECKED_OUT);
        return attendanceRepository.save(record);
    }

    public Map<String, Object> getServiceStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "Attendance Management System");
        status.put("status", "UP");
        status.put("version", "1.0.0");
        status.put("timestamp", LocalDateTime.now().toString());
        status.put("totalRecords", attendanceRepository.count());
        status.put("currentlyCheckedIn", attendanceRepository.countCurrentlyCheckedIn());
        status.put("environment", System.getenv().getOrDefault("APP_ENV", "development"));
        status.put("instanceId", System.getenv().getOrDefault("INSTANCE_ID", "local"));
        return status;
    }

    public List<AttendanceRecord> getAllRecords() {
        return attendanceRepository.findAll();
    }

    public List<AttendanceRecord> getRecordsByEmployee(String employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId);
    }

    public List<AttendanceRecord> getTodayRecords() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1).minusSeconds(1);
        return attendanceRepository.findByDateRange(startOfDay, endOfDay);
    }

    public List<AttendanceRecord> getCurrentlyCheckedIn() {
        return attendanceRepository.findByStatus(AttendanceRecord.AttendanceStatus.CHECKED_IN);
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long totalToday = getTodayRecords().size();
        long checkedIn = attendanceRepository.countCurrentlyCheckedIn();
        long totalAll = attendanceRepository.count();

        stats.put("totalToday", totalToday);
        stats.put("currentlyCheckedIn", checkedIn);
        stats.put("totalRecords", totalAll);
        stats.put("checkedOut", totalToday - checkedIn);
        return stats;
    }
}
