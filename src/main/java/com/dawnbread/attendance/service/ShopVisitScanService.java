package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopVisitDaySummaryDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRecordDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRequest;
import com.dawnbread.attendance.dto.ShopVisitScanResponseDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GeofenceStatus;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.ShopVisitScan;
import com.dawnbread.attendance.entity.ShopVisitStatus;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.ShopVisitScanRepository;
import com.dawnbread.attendance.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * QR shop-visit flow (Q1): resolves a scanned code to a shop, verifies the
 * salesman's GPS against it, and records the attempt — pass or fail,
 * always. Follows the security rule from the spec exactly: QR Scan -&gt;
 * Shop Code -&gt; Shop Lookup -&gt; GPS -&gt; Geofence Validation -&gt; Visit
 * Record. Every decision is made here, server-side, from the stored Agent
 * and CustomerShop records — never from anything the client merely claims.
 */
@Service
@Transactional
public class ShopVisitScanService {

    @Autowired
    private AgentService agentService;

    @Autowired
    private CustomerShopService customerShopService;

    @Autowired
    private ShopRequirementService shopRequirementService;

    @Autowired
    private LmtSettingsService lmtSettingsService;

    @Autowired
    private ShopVisitScanRepository shopVisitScanRepository;

    // Read-only use in the same company check-in gate submitShopVisit
    // already enforces for LMT — never written here.
    @Autowired
    private AttendanceRepository attendanceRepository;

    public ShopVisitScanResponseDTO recordScan(ShopVisitScanRequest request) {
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + request.getAgentId()));

        // Mirrors SalesService.submitShopVisit's own company check-in gate
        // exactly, and for the same reason: an LMT must be on duty before
        // doing anything shop-related (matches the spec's own flow
        // diagram, "Check In at Dawn Bread" before "Scan Shop QR").
        // SALESMAN_LOCAL has no check-in concept at all, matching its
        // existing shop-visit behavior.
        boolean isLocalSalesman = "SALESMAN_LOCAL".equals(agent.getRole());
        if (!isLocalSalesman) {
            Attendance openAttendance = attendanceRepository.findOpenAttendanceByAgentId(agent.getId()).orElse(null);
            if (openAttendance == null || openAttendance.getMart() == null
                    || openAttendance.getMart().getMartType() != MartType.COMPANY) {
                throw new IllegalArgumentException("You must check in at the company before scanning a shop.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ShopVisitScan scan = new ShopVisitScan();
        scan.setAgentId(agent.getId());
        scan.setScannedCode(request.getScannedCode().trim());
        scan.setScanDate(now.toLocalDate());
        scan.setScanTime(now.toLocalTime());
        scan.setGpsLatitude(request.getLatitude());
        scan.setGpsLongitude(request.getLongitude());
        scan.setCreatedAt(now);

        Optional<CustomerShop> shopOpt = customerShopService.getByShopCode(scan.getScannedCode());
        if (shopOpt.isEmpty()) {
            scan.setVisitStatus(ShopVisitStatus.INVALID_CODE);
            scan.setGeofenceStatus(GeofenceStatus.NOT_EVALUATED);
            ShopVisitScan saved = shopVisitScanRepository.save(scan);
            return buildResponse(saved, null, "No shop registered with this code: " + scan.getScannedCode());
        }

        CustomerShop shop = shopOpt.get();
        scan.setCustomerShop(shop);

        if (!Boolean.TRUE.equals(shop.getIsActive())) {
            scan.setVisitStatus(ShopVisitStatus.SHOP_INACTIVE);
            scan.setGeofenceStatus(GeofenceStatus.NOT_EVALUATED);
            ShopVisitScan saved = shopVisitScanRepository.save(scan);
            return buildResponse(saved, shop, "Customer shop '" + shop.getShopName() + "' is not active.");
        }

        // Distance is computed whenever the shop HAS coordinates, regardless
        // of whether geofence enforcement is actually required right now —
        // so the admin dashboard can always show Inside/Outside, even for a
        // shop where geofence isn't currently enforced.
        Double distance = null;
        GeofenceStatus geofenceStatus = GeofenceStatus.NOT_EVALUATED;
        double buffer = lmtSettingsService.getOrCreate().getGeofenceBufferMeters();
        Double allowedRadius = null;
        if (shop.getLatitude() != null && shop.getLongitude() != null && shop.getRadius() != null) {
            distance = GeoUtils.distanceMeters(request.getLatitude(), request.getLongitude(), shop.getLatitude(), shop.getLongitude());
            allowedRadius = shop.getRadius() + buffer;
            geofenceStatus = distance <= allowedRadius ? GeofenceStatus.INSIDE : GeofenceStatus.OUTSIDE;
        }
        scan.setDistanceFromShopMeters(distance);
        scan.setGeofenceStatus(geofenceStatus);

        boolean geofenceRequired = shopRequirementService.isGeofenceRequired(shop);

        // Mirrors submitShopVisit's existing role split exactly: a Local
        // salesman has no check-in to prove presence, so a shop with no
        // location configured can't be verified at all for them — hard
        // fail. LMT keeps its existing leniency (an unconfigured shop is
        // simply not gated). No distinct status exists for "can't verify";
        // grouped under OUTSIDE_GEOFENCE as the closest fit.
        if (isLocalSalesman && geofenceRequired && geofenceStatus == GeofenceStatus.NOT_EVALUATED) {
            scan.setVisitStatus(ShopVisitStatus.OUTSIDE_GEOFENCE);
            ShopVisitScan saved = shopVisitScanRepository.save(scan);
            return buildResponse(saved, shop, "'" + shop.getShopName()
                    + "' has no location set up yet, so a visit here can't be verified. Ask the admin to set the shop's location.");
        }

        if (geofenceRequired && geofenceStatus == GeofenceStatus.OUTSIDE) {
            scan.setVisitStatus(ShopVisitStatus.OUTSIDE_GEOFENCE);
            ShopVisitScan saved = shopVisitScanRepository.save(scan);
            return buildResponse(saved, shop, String.format(
                    "Too far from %s: %.0fm away, allowed up to %.0fm (shop radius %.0fm + %.0fm buffer).",
                    shop.getShopName(), distance, allowedRadius, shop.getRadius(), buffer));
        }

        scan.setVisitStatus(ShopVisitStatus.SUCCESS);
        ShopVisitScan saved = shopVisitScanRepository.save(scan);
        return buildResponse(saved, shop, "Shop verified.");
    }

    private ShopVisitScanResponseDTO buildResponse(ShopVisitScan scan, CustomerShop shop, String message) {
        ShopVisitScanResponseDTO dto = new ShopVisitScanResponseDTO();
        dto.setVisitId(scan.getId());
        dto.setVisitStatus(scan.getVisitStatus().name());
        dto.setGeofenceStatus(scan.getGeofenceStatus().name());
        dto.setDistanceMeters(scan.getDistanceFromShopMeters());
        dto.setMessage(message);
        if (shop != null) {
            dto.setShop(customerShopService.toDTO(shop));
        }
        return dto;
    }

    /**
     * Q2 — the admin's "QR / Shop Visits" report: every scan attempt (pass
     * or fail) in a date range, across all salesmen or one. Mirrors
     * LmtStockService.getReconciliationReport's exact shape (agentId
     * optional, date range required).
     */
    public List<ShopVisitScanRecordDTO> getReport(LocalDate startDate, LocalDate endDate, Long agentId) {
        List<ShopVisitScan> scans = agentId != null
                ? shopVisitScanRepository.findByAgentIdAndScanDateBetweenWithShop(agentId, startDate, endDate)
                : shopVisitScanRepository.findByScanDateBetweenWithShop(startDate, endDate);
        return scans.stream().map(this::toRecordDTO).collect(Collectors.toList());
    }

    /** Q2 — one salesman's visit history for one day, plus the four summary counts from the spec's example screen. */
    public ShopVisitDaySummaryDTO getDaySummary(Long agentId, LocalDate date) {
        List<ShopVisitScan> scans = shopVisitScanRepository.findByAgentIdAndScanDateWithShop(agentId, date);

        long successful = scans.stream().filter(s -> s.getVisitStatus() == ShopVisitStatus.SUCCESS).count();
        Set<Long> uniqueShopIds = scans.stream()
                .filter(s -> s.getCustomerShop() != null)
                .map(s -> s.getCustomerShop().getId())
                .collect(Collectors.toSet());

        ShopVisitDaySummaryDTO dto = new ShopVisitDaySummaryDTO();
        dto.setAgentId(agentId);
        dto.setDate(date);
        dto.setTotalVisits(scans.size());
        dto.setSuccessfulVisits((int) successful);
        dto.setFailedVisits(scans.size() - (int) successful);
        dto.setUniqueShopsVisited(uniqueShopIds.size());
        dto.setVisits(scans.stream().map(this::toRecordDTO).collect(Collectors.toList()));
        return dto;
    }

    private ShopVisitScanRecordDTO toRecordDTO(ShopVisitScan scan) {
        ShopVisitScanRecordDTO dto = new ShopVisitScanRecordDTO();
        dto.setVisitId(scan.getId());
        dto.setAgentId(scan.getAgentId());
        dto.setScannedCode(scan.getScannedCode());
        dto.setScanDate(scan.getScanDate());
        dto.setScanTime(scan.getScanTime());
        dto.setDistanceMeters(scan.getDistanceFromShopMeters());
        dto.setGeofenceStatus(scan.getGeofenceStatus().name());
        dto.setVisitStatus(scan.getVisitStatus().name());

        CustomerShop shop = scan.getCustomerShop();
        if (shop != null) {
            dto.setShopId(shop.getId());
            dto.setShopCode(shop.getShopCode());
            dto.setShopName(shop.getShopName());
            dto.setAreaName(shop.getArea() != null ? shop.getArea().getName() : null);
            dto.setCity(parseCity(shop.getShopCode()));
        }
        return dto;
    }

    /** "ISB" from "ISB-I14-001" — the confirmed CITY-SECTOR-NUMBER shop code convention. Null if the code has no "-" (so it never silently mislabels a differently-shaped code). */
    private String parseCity(String shopCode) {
        if (shopCode == null) {
            return null;
        }
        int dash = shopCode.indexOf('-');
        return dash > 0 ? shopCode.substring(0, dash) : null;
    }
}
