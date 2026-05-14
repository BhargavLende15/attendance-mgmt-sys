package com.attendance;

import com.attendance.controller.AttendanceController;
import com.attendance.model.AttendanceRecord;
import com.attendance.model.CheckInRequest;
import com.attendance.service.AttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttendanceManagementApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AttendanceService attendanceService;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/attendance";
    }

    @Test
    void contextLoads() {
        assertThat(attendanceService).isNotNull();
    }

    @Test
    void testGetStatus_ReturnsOk() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/status", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void testGetStatus_ContainsRequiredFields() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/status", Map.class);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("service", "status", "version", "timestamp");
    }

    @Test
    void testCheckIn_ValidRequest_ReturnsCreated() {
        CheckInRequest request = new CheckInRequest();
        request.setEmployeeId("EMP001");
        request.setEmployeeName("Alice Johnson");
        request.setDepartment("Engineering");
        request.setNotes("Test check-in");

        ResponseEntity<AttendanceRecord> response = restTemplate.postForEntity(
            baseUrl + "/checkin", request, AttendanceRecord.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmployeeId()).isEqualTo("EMP001");
        assertThat(response.getBody().getStatus()).isEqualTo(AttendanceRecord.AttendanceStatus.CHECKED_IN);
    }

    @Test
    void testCheckIn_MissingEmployeeId_ReturnsBadRequest() {
        CheckInRequest request = new CheckInRequest();
        request.setEmployeeName("Bob Smith");
        request.setDepartment("HR");

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/checkin", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testGetAllRecords_ReturnsOk() {
        ResponseEntity<Object[]> response = restTemplate.getForEntity(baseUrl + "/records", Object[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void testGetDashboard_ReturnsStats() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl + "/dashboard", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("totalToday", "currentlyCheckedIn", "totalRecords");
    }

    @Test
    void testGetTodayRecords_ReturnsOk() {
        ResponseEntity<Object[]> response = restTemplate.getForEntity(baseUrl + "/records/today", Object[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testGetActiveEmployees_ReturnsOk() {
        ResponseEntity<Object[]> response = restTemplate.getForEntity(baseUrl + "/active", Object[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
