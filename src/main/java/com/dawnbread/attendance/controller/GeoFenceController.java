package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.GeoFenceRequest;
import com.dawnbread.attendance.dto.GeoFenceResponse;
import com.dawnbread.attendance.entity.GeoFenceLog;
import com.dawnbread.attendance.service.GeoFencingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geo-fence")
@CrossOrigin(origins = "*")
public class GeoFenceController {

    @Autowired
    private GeoFencingService geoFencingService;

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

    @GetMapping("/logs/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<GeoFenceLog>>> getLogsForAgent(@PathVariable Long agentId) {
        try {
            List<GeoFenceLog> logs = geoFencingService.getLogsForAgent(agentId);
            return ResponseEntity.ok(ApiResponse.success("Geo-fence logs retrieved for agent", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<GeoFenceLog>>> getAllLogs() {
        try {
            List<GeoFenceLog> logs = geoFencingService.getAllLogs();
            return ResponseEntity.ok(ApiResponse.success("All geo-fence logs retrieved", logs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
