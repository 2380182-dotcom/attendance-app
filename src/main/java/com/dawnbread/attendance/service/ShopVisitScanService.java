package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.NotVisitedShopDTO;
import com.dawnbread.attendance.dto.QrVisitSummaryDTO;
import com.dawnbread.attendance.dto.ShopVisitDaySummaryDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRecordDTO;
import com.dawnbread.attendance.dto.ShopVisitScanRequest;
import com.dawnbread.attendance.dto.ShopVisitScanResponseDTO;
import com.dawnbread.attendance.dto.VoucherWithoutScanDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GeofenceStatus;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.entity.ShopVisitScan;
import com.dawnbread.attendance.entity.ShopVisitStatus;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import com.dawnbread.attendance.repository.ShopVisitScanRepository;
import com.dawnbread.attendance.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    // Same rationale as AttendanceService.KARACHI_ZONE / FaceVerificationService.KARACHI_ZONE:
    // the server runs on UTC (Render) but every salesman and admin is in
    // Pakistan — a scan between UTC midnight and ~5am (i.e. before 5am, up
    // to Karachi's own midnight) would otherwise land on the wrong calendar
    // day for "today" checks, the Not Visited report, and voucher-without-
    // scan cross-referencing. Unlike SalesRecord.saleTime (see SalesService),
    // nothing anywhere converts scanTime for display, so fixing the write
    // site here is a pure improvement with no historical-display tradeoff.
    // Injected (TimeConfig) rather than a bare ZoneId so a test can prove
    // the exact day-boundary behavior with a fixed instant.
    @Autowired
    private java.time.Clock clock;

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

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private SalesRecordRepository salesRecordRepository;

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

        LocalDateTime now = LocalDateTime.now(clock);
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
     * Task 3 — the admin's "QR / Shop Visits" report, server-side paginated
     * and filtered (date range, optional agentId/role/shopSearch/failedOnly).
     * Replaces the old unpaginated getReport — only the dashboard's
     * QrShopVisitsPage called it, rewritten in the same change.
     * voucherCreated is computed in one bulk query per page, never per row.
     */
    public Page<ShopVisitScanRecordDTO> getPagedReport(LocalDate startDate, LocalDate endDate, Long agentId,
                                                         String role, String shopSearch, boolean failedOnly,
                                                         Pageable pageable) {
        // "" means no filter for these two String params — never null (see
        // findFiltered's doc: a null String bound inside LOWER(CONCAT(...))
        // or compared via "=" crashes on real Postgres).
        Page<ShopVisitScan> page = shopVisitScanRepository.findFiltered(
                startDate, endDate, agentId, role == null ? "" : role,
                (shopSearch == null || shopSearch.isBlank()) ? "" : shopSearch.trim(),
                failedOnly, pageable);

        Set<String> voucheredKeys = new HashSet<>();
        for (Object[] row : salesRecordRepository.findAgentShopDatePairsWithVoucherBetween(startDate, endDate)) {
            voucheredKeys.add(row[0] + ":" + row[1] + ":" + row[2]);
        }

        return page.map(scan -> {
            ShopVisitScanRecordDTO dto = toRecordDTO(scan);
            if (scan.getCustomerShop() != null) {
                dto.setVoucherCreated(voucheredKeys.contains(scan.getAgentId() + ":" + scan.getCustomerShop().getId() + ":" + scan.getScanDate()));
            } else {
                dto.setVoucherCreated(false);
            }
            return dto;
        });
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

    private static final String ROLE_LMT = "SALESMAN_LMT";
    private static final String ROLE_LOCAL = "SALESMAN_LOCAL";

    /**
     * Task 3/4 "Not Visited" — corrected to treat Local and LMT identically:
     * both mean the salesman's ASSIGNED outlets weren't scanned. Assignment
     * (CustomerShop.assignedAgent) is the ONE mechanism for both sections —
     * there was never a separate pre-existing one (confirmed by a full
     * codebase/git-history audit) — so this simply reads it, filtered by
     * the assigned agent's role. An active shop with NO salesman assigned
     * is deliberately excluded here (it belongs in the separate
     * "Unassigned — not scanned" list below, not silently folded into
     * either section's Not Visited count).
     */
    private List<NotVisitedShopDTO> computeNotVisited(LocalDate date, String role, Long agentId) {
        List<NotVisitedShopDTO> result = new ArrayList<>();
        String label = ROLE_LOCAL.equals(role) ? "LOCAL" : "LMT";
        List<CustomerShop> assignedShops = agentId != null
                ? customerShopRepository.findByIsActiveTrueAndAssignedAgentId(agentId)
                : customerShopRepository.findByIsActiveTrueAndAssignedAgentRole(role);
        Map<Long, Set<Long>> scannedByAgent = new HashMap<>();
        for (CustomerShop shop : assignedShops) {
            Long shopAgentId = shop.getAssignedAgent().getId();
            Set<Long> scanned = scannedByAgent.computeIfAbsent(shopAgentId,
                    id -> shopVisitScanRepository.findSuccessfullyScannedShopIdsForAgentAndDate(id, date));
            if (!scanned.contains(shop.getId())) {
                result.add(toNotVisitedDTO(shop, label));
            }
        }
        return result;
    }

    /**
     * Task 4 correction: active shops with NO salesman assigned at all,
     * that also weren't scanned on this date — a separate list from
     * computeNotVisited so an unassigned shop is never simply invisible to
     * either report. Not scoped to a role or agent — an unassigned shop
     * has no section of its own, so it's the same list under both tabs.
     */
    private List<NotVisitedShopDTO> computeUnassignedNotScanned(LocalDate date) {
        List<CustomerShop> unassignedShops = customerShopRepository.findByIsActiveTrueAndAssignedAgentIsNull();
        Set<Long> scannedShopIds = shopVisitScanRepository.findSuccessfullyScannedShopIdsForDate(date);
        return unassignedShops.stream()
                .filter(shop -> !scannedShopIds.contains(shop.getId()))
                .map(shop -> toNotVisitedDTO(shop, "UNASSIGNED"))
                .collect(Collectors.toList());
    }

    public Page<NotVisitedShopDTO> getUnassignedNotScanned(LocalDate date, String shopSearch, Pageable pageable) {
        List<NotVisitedShopDTO> all = computeUnassignedNotScanned(date);
        if (shopSearch != null && !shopSearch.isBlank()) {
            String q = shopSearch.trim().toLowerCase();
            all = all.stream()
                    .filter(d -> (d.getShopName() != null && d.getShopName().toLowerCase().contains(q))
                            || (d.getShopCode() != null && d.getShopCode().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }
        return paginate(all, pageable);
    }

    public Page<NotVisitedShopDTO> getNotVisited(LocalDate date, String role, Long agentId, String shopSearch, Pageable pageable) {
        List<NotVisitedShopDTO> all = computeNotVisited(date, role, agentId);
        if (shopSearch != null && !shopSearch.isBlank()) {
            String q = shopSearch.trim().toLowerCase();
            all = all.stream()
                    .filter(d -> (d.getShopName() != null && d.getShopName().toLowerCase().contains(q))
                            || (d.getShopCode() != null && d.getShopCode().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }
        return paginate(all, pageable);
    }

    /**
     * Task 3/4 summary counts (Total Shops / Visited / Not Visited /
     * Voucher-Without-Scan) for one date + role. Total/Visited/Not Visited
     * are scoped to shops ASSIGNED to this role now (Local and LMT treated
     * identically) — an unassigned shop is never counted here; see
     * getUnassignedNotScanned for that separate bucket.
     */
    public QrVisitSummaryDTO getSummaryCounts(LocalDate date, String role, Long agentId) {
        int totalShops = agentId != null
                ? customerShopRepository.findByIsActiveTrueAndAssignedAgentId(agentId).size()
                : customerShopRepository.findByIsActiveTrueAndAssignedAgentRole(role).size();
        int notVisited = computeNotVisited(date, role, agentId).size();
        int visited = totalShops - notVisited;
        int voucherWithoutScan = computeVouchersWithoutScan(date, date, agentId, role).size();
        return new QrVisitSummaryDTO(totalShops, visited, notVisited, voucherWithoutScan);
    }

    /** Task 3 "voucher without scan": a SalesRecord exists for this agent/shop/day but no successful scan does. */
    private List<VoucherWithoutScanDTO> computeVouchersWithoutScan(LocalDate startDate, LocalDate endDate, Long agentId, String role) {
        List<SalesRecord> records = salesRecordRepository.findBySaleDateBetweenWithShopNotNull(startDate, endDate);
        Set<String> scannedKeys = new HashSet<>();
        for (Object[] row : shopVisitScanRepository.findSuccessfulScanKeysBetween(startDate, endDate)) {
            scannedKeys.add(row[0] + ":" + row[1] + ":" + row[2]);
        }
        List<VoucherWithoutScanDTO> result = new ArrayList<>();
        for (SalesRecord r : records) {
            if (r.getAgent() == null || r.getCustomerShop() == null) {
                continue;
            }
            if (agentId != null && !agentId.equals(r.getAgent().getId())) {
                continue;
            }
            if (role != null && !role.equals(r.getAgent().getRole())) {
                continue;
            }
            String key = r.getAgent().getId() + ":" + r.getCustomerShop().getId() + ":" + r.getSaleDate();
            if (!scannedKeys.contains(key)) {
                result.add(toVoucherWithoutScanDTO(r));
            }
        }
        return result;
    }

    public Page<VoucherWithoutScanDTO> getVouchersWithoutScan(LocalDate startDate, LocalDate endDate, Long agentId, String role, Pageable pageable) {
        return paginate(computeVouchersWithoutScan(startDate, endDate, agentId, role), pageable);
    }

    private NotVisitedShopDTO toNotVisitedDTO(CustomerShop shop, String role) {
        NotVisitedShopDTO dto = new NotVisitedShopDTO();
        dto.setShopId(shop.getId());
        dto.setShopCode(shop.getShopCode());
        dto.setShopName(shop.getShopName());
        dto.setAreaName(shop.getArea() != null ? shop.getArea().getName() : null);
        dto.setCity(parseCity(shop.getShopCode()));
        dto.setRole(role);
        if (shop.getAssignedAgent() != null) {
            dto.setAssignedAgentId(shop.getAssignedAgent().getId());
            dto.setAssignedAgentName(shop.getAssignedAgent().getName());
        }
        return dto;
    }

    private VoucherWithoutScanDTO toVoucherWithoutScanDTO(SalesRecord record) {
        VoucherWithoutScanDTO dto = new VoucherWithoutScanDTO();
        dto.setSalesRecordId(record.getId());
        dto.setAgentId(record.getAgent().getId());
        dto.setShopId(record.getCustomerShop().getId());
        dto.setShopCode(record.getCustomerShop().getShopCode());
        dto.setShopName(record.getCustomerShop().getShopName());
        dto.setSaleDate(record.getSaleDate());
        dto.setSaleTime(record.getSaleTime());
        dto.setTotalAmount(record.getTotalAmount());
        return dto;
    }

    /** Manual in-memory pagination for reports that can't be expressed as a single SQL page (Not Visited / voucher-without-scan both require a Java-side set difference). */
    private <T> Page<T> paginate(List<T> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
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
