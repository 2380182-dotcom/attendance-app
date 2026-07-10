package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.GeoFenceLogDTO;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/geo-fence")
public class GeoFenceController {

    @Autowired
    private GeoFencingService geoFencingService;

    @Autowired
    private HttpServletRequest request;

    /**
     * Called by agents during check-in/out to verify distance from the mart.
     * Self-or-management: an agent may only submit their own presence
     * events. Previously had no check at all — any authenticated agent could
     * submit geofence events under a coworker's agentId, writing log rows
     * and triggering real "verification required" push notifications on
     * their behalf.
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<GeoFenceResponse>> checkGeoFence(@RequestBody GeoFenceRequest geoFenceRequest) {
        if (!AccessControl.isSelfOrRole(this.request, geoFenceRequest.getAgentId(), "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("You can only report your own location."));
        }
        try {
            GeoFenceResponse response = geoFencingService.checkGeoFenceStatus(
                    geoFenceRequest.getAgentId(),
                    geoFenceRequest.getLatitude(),
                    geoFenceRequest.getLongitude()
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
     *
     * Returns GeoFenceLogDTO, not the entity — GeoFenceLog.agent is an eager
     * @ManyToOne with a public getter, so returning the entity directly
     * serialized the agent's full record (email, phone, shift config, and —
     * before Agent gained field-level @JsonIgnore — its password hash and
     * raw face embedding) into every log row.
     */
    @GetMapping("/logs/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<GeoFenceLogDTO>>> getLogsForAgent(@PathVariable Long agentId) {
        if (!AccessControl.hasRole(this.request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator or HR can view geo-fence logs."));
        }
        try {
            List<GeoFenceLog> logs = geoFencingService.getLogsForAgent(agentId);
            return ResponseEntity.ok(ApiResponse.success("Geo-fence logs retrieved for agent", convertToDTOs(logs)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<GeoFenceLogDTO>>> getAllLogs() {
        if (!AccessControl.hasRole(this.request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator or HR can view geo-fence logs."));
        }
        try {
            List<GeoFenceLog> logs = geoFencingService.getAllLogs();
            return ResponseEntity.ok(ApiResponse.success("All geo-fence logs retrieved", convertToDTOs(logs)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private List<GeoFenceLogDTO> convertToDTOs(List<GeoFenceLog> logs) {
        return logs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private GeoFenceLogDTO convertToDTO(GeoFenceLog log) {
        GeoFenceLogDTO dto = new GeoFenceLogDTO();
        dto.setId(log.getId());
        if (log.getAgent() != null) {
            dto.setAgentId(log.getAgent().getId());
            dto.setAgentDisplayId(log.getAgent().getAgentId());
            dto.setAgentName(log.getAgent().getName());
        }
        if (log.getMart() != null) {
            dto.setMartId(log.getMart().getId());
            dto.setMartName(log.getMart().getName());
        }
        dto.setAction(log.getAction());
        dto.setLatitude(log.getLatitude());
        dto.setLongitude(log.getLongitude());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
