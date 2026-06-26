package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CheckInRequest;
import com.dawnbread.attendance.dto.CheckOutRequest;
import com.dawnbread.attendance.dto.GeoFenceResponse;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.GeoFenceLog;
import com.dawnbread.attendance.entity.Mart;
import com.dawnbread.attendance.repository.AgentRepository;
import com.dawnbread.attendance.repository.GeoFenceLogRepository;
import com.dawnbread.attendance.repository.MartRepository;
import com.dawnbread.attendance.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GeoFencingService {

    @Autowired
    private GeoFenceLogRepository geoFenceLogRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private MartRepository martRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private NotificationService notificationService;

    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // meters
    }

    public GeoFenceResponse checkGeoFenceStatus(Long agentId, Double latitude, Double longitude) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agent not found with id: " + agentId));

        List<Mart> marts = martRepository.findAll();
        Mart insideMart = null;

        for (Mart mart : marts) {
            if (Boolean.TRUE.equals(mart.getGeoFencingEnabled())) {
                double distance = calculateDistance(latitude, longitude, mart.getLatitude(), mart.getLongitude());
                if (distance <= mart.getRadius()) {
                    insideMart = mart;
                    break;
                }
            }
        }

        Optional<Attendance> openAttendance = attendanceRepository.findOpenAttendanceByAgentId(agentId);

        if (insideMart != null) {
            if (openAttendance.isEmpty()) {
                CheckInRequest checkInRequest = new CheckInRequest();
                checkInRequest.setAgentId(agentId);
                checkInRequest.setMartId(insideMart.getId());
                checkInRequest.setLatitude(latitude);
                checkInRequest.setLongitude(longitude);

                Attendance attendance = attendanceService.checkIn(checkInRequest);
                
                GeoFenceLog log = new GeoFenceLog();
                log.setAgent(agent);
                log.setMart(insideMart);
                log.setAction("ENTERED");
                log.setLatitude(latitude);
                log.setLongitude(longitude);
                log.setCreatedAt(LocalDateTime.now());
                geoFenceLogRepository.save(log);

                notificationService.sendPushNotification(agentId, "Auto Checked-In", "You have been checked in automatically at " + insideMart.getName());

                return new GeoFenceResponse("ENTERED", "Auto Check-In successful at " + insideMart.getName(), attendance);
            } else {
                return new GeoFenceResponse("STAYED", "Agent is active inside " + insideMart.getName(), openAttendance.get());
            }
        } else {
            if (openAttendance.isPresent()) {
                Attendance attendance = openAttendance.get();
                Mart mart = attendance.getMart();

                CheckOutRequest checkOutRequest = new CheckOutRequest();
                checkOutRequest.setAgentId(agentId);
                checkOutRequest.setLatitude(latitude);
                checkOutRequest.setLongitude(longitude);

                Attendance updatedAttendance = attendanceService.checkOut(checkOutRequest);

                GeoFenceLog log = new GeoFenceLog();
                log.setAgent(agent);
                log.setMart(mart);
                log.setAction("EXITED");
                log.setLatitude(latitude);
                log.setLongitude(longitude);
                log.setCreatedAt(LocalDateTime.now());
                geoFenceLogRepository.save(log);

                notificationService.sendPushNotification(agentId, "Auto Checked-Out", "You have been checked out automatically from " + mart.getName());

                return new GeoFenceResponse("EXITED", "Auto Check-Out successful from " + mart.getName(), updatedAttendance);
            } else {
                return new GeoFenceResponse("OUTSIDE", "Agent is outside all geo-fences", null);
            }
        }
    }

    public List<GeoFenceLog> getLogsForAgent(Long agentId) {
        return geoFenceLogRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    public List<GeoFenceLog> getAllLogs() {
        return geoFenceLogRepository.findAll();
    }
}
