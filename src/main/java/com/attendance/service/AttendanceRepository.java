package com.attendance.service;

import com.attendance.model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByEmployeeId(String employeeId);

    List<AttendanceRecord> findByDepartment(String department);

    Optional<AttendanceRecord> findTopByEmployeeIdOrderByCheckInTimeDesc(String employeeId);

    List<AttendanceRecord> findByStatus(AttendanceRecord.AttendanceStatus status);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.checkInTime >= :startTime AND a.checkInTime <= :endTime")
    List<AttendanceRecord> findByDateRange(LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT COUNT(DISTINCT a.employeeId) FROM AttendanceRecord a WHERE a.status = 'CHECKED_IN'")
    Long countCurrentlyCheckedIn();
}
