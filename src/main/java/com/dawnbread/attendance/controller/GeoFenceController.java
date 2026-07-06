package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.GeoFenceRequest;
import com.dawnbread.attendance.dto.GeoFenceResponse;
import com.dawnbread.attendance.entity.GeoFenceLog;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.GeoFencingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geo-fence")
public class GeoFenceController {

    @Autowired
    private GeoFencingService geoFencingService;

    @Autowired
    private HttpServletRequest request;

    /**
     * Called by agents during check-in/out to verify distance from the mart —
     * stays open to any authenticated role, not just self-scoped, since it's a
     * point-in-time distance check, not a record read.
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<GeoFenceResponse>> checkGeoFence(@RequestBody GeoFenceRequest request) {
        try {
            GeoFenceResponse response = geoFencingService.checkGeoFenceStatus(
                    request.getAgentId(),
                    request.getLatitude(),
                    request.getLongitude()
            );
            return ResponseEntity.ok(ApiResponse.success("Geo-fence status verified", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Location history for an agent. Admin/HR only — not currently called by
     * the mobile app at all, but a bare Agent/Sales token could otherwise read
     * any other agent's location log history.
     */
    @GetMapping("/logs/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<GeoFenceLog>>> getLogsForAgent(@PathVariable Long agentId) {
        if (!AccessControl.hasRole(this.request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator or HR can view geo-fence logs."));
        }
        try {
            List<GeoFenceLog> logs = geoFencingService.getLogsForAgent(agentId);
            return ResponseEntity.ok(ApiResponse.success("Geo-fence logs retrieved for agent", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<GeoFenceLog>>> getAllLogs() {
        if (!AccessControl.hasRole(this.request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator or HR can view geo-fence logs."));
        }
        try {
            List<GeoFenceLog> logs = geoFencingService.getAllLogs();
            return ResponseEntity.ok(ApiResponse.success("All geo-fence logs retrieved", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
