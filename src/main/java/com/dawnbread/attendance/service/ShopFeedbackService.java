package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.ShopFeedbackRequest;
import com.dawnbread.attendance.dto.ShopFeedbackResponseDTO;
import com.dawnbread.attendance.entity.Agent;
import com.dawnbread.attendance.entity.Attendance;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.GeofenceStatus;
import com.dawnbread.attendance.entity.MartType;
import com.dawnbread.attendance.entity.ShopFeedback;
import com.dawnbread.attendance.entity.ShopFeedbackReason;
import com.dawnbread.attendance.repository.AttendanceRepository;
import com.dawnbread.attendance.repository.ShopFeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * "Salesman updates" feature: a salesman reached a shop but couldn't make
 * a sale, so he records feedback instead — no QR scan, no voucher, but the
 * same GPS/geofence rule as a real visit (reused from ShopRequirementService,
 * the exact gate SalesService.submitShopVisit enforces) and the same
 * company check-in gate ShopVisitScanService.recordScan enforces for LMT.
 */
@Service
@Transactional
public class ShopFeedbackService {

    @Autowired
    private Clock clock;

    @Autowired
    private AgentService agentService;

    @Autowired
    private CustomerShopService customerShopService;

    @Autowired
    private ShopRequirementService shopRequirementService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private ShopFeedbackRepository shopFeedbackRepository;

    public ShopFeedbackResponseDTO recordFeedback(ShopFeedbackRequest request) {
        Agent agent = agentService.getAgentById(request.getAgentId())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found with ID: " + request.getAgentId()));

        // Idempotency: a reused requestId returns the already-saved
        // feedback rather than re-running the gates (mirrors
        // SalesService.submitShopVisit's early-return for the same reason —
        // a retry after a flaky network response must not fail the
        // geofence/check-in gates a second time for no reason).
        String requestId = request.getRequestId();
        if (requestId != null && !requestId.isBlank()) {
            ShopFeedback existing = shopFeedbackRepository.findByRequestId(requestId).orElse(null);
            if (existing != null) {
                return toResponseDTO(existing);
            }
        }

        ShopFeedbackReason reason;
        try {
            reason = ShopFeedbackReason.valueOf(request.getReason().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("reason must be SHOP_CLOSED, STOCK_FULL, OWNER_NOT_AVAILABLE, or OTHER — got: " + request.getReason());
        }
        if (reason == ShopFeedbackReason.OTHER && (request.getNote() == null || request.getNote().isBlank())) {
            throw new IllegalArgumentException("A note is required when reason is OTHER.");
        }

        // Same company check-in gate as SalesService.submitShopVisit and
        // ShopVisitScanService.recordScan — SALESMAN_LOCAL has no duty
        // concept, every other role must be on duty at a COMPANY mart.
        boolean isLocalSalesman = "SALESMAN_LOCAL".equals(agent.getRole());
        if (!isLocalSalesman) {
            Attendance openAttendance = attendanceRepository.findOpenAttendanceByAgentId(agent.getId()).orElse(null);
            if (openAttendance == null || openAttendance.getMart() == null
                    || openAttendance.getMart().getMartType() != MartType.COMPANY) {
                throw new IllegalArgumentException("You must check in at the company before recording feedback.");
            }
        }

        CustomerShop shop = customerShopService.getByShopCode(request.getShopCode())
                .orElseThrow(() -> new IllegalArgumentException("No customer shop registered with code: " + request.getShopCode()));
        if (!Boolean.TRUE.equals(shop.getIsActive())) {
            throw new IllegalArgumentException("Customer shop '" + request.getShopCode() + "' is not active.");
        }

        // Same buffered geofence gate as a real visit — GPS IS checked for
        // feedback, per the feature spec, reusing the exact shared rule.
        Double distance = shopRequirementService.checkGeofenceOrThrow(
                shop, isLocalSalesman, request.getLatitude(), request.getLongitude());

        ShopFeedback feedback = new ShopFeedback();
        feedback.setAgentId(agent.getId());
        feedback.setCustomerShop(shop);
        feedback.setReason(reason);
        feedback.setNote(request.getNote());
        feedback.setGpsLatitude(request.getLatitude());
        feedback.setGpsLongitude(request.getLongitude());
        feedback.setDistanceFromShopMeters(distance);
        feedback.setGeofenceStatus(distance != null ? GeofenceStatus.INSIDE : GeofenceStatus.NOT_EVALUATED);
        LocalDateTime now = LocalDateTime.now(clock);
        feedback.setVisitDate(now.toLocalDate());
        feedback.setVisitTime(now.toLocalTime());
        feedback.setRequestId(requestId != null && !requestId.isBlank() ? requestId : null);
        feedback.setCreatedAt(now);

        ShopFeedback saved;
        try {
            saved = shopFeedbackRepository.save(feedback);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Same race as SalesService.submitShopVisit: two concurrent
            // submissions with the same requestId can both pass the
            // findByRequestId check above before either commits. Unlike
            // the voucher path, feedback has no side effects to "resolve"
            // in a fresh transaction — the unique index guarantees exactly
            // one row exists, so simply re-reading it (in a fresh,
            // unpoisoned request/transaction) is enough. We surface a
            // clear, retryable error rather than guessing which concurrent
            // caller "won".
            throw new IllegalArgumentException("This feedback was already being saved — please check and retry if needed.");
        }

        return toResponseDTO(saved);
    }

    private ShopFeedbackResponseDTO toResponseDTO(ShopFeedback feedback) {
        ShopFeedbackResponseDTO dto = new ShopFeedbackResponseDTO();
        dto.setId(feedback.getId());
        if (feedback.getCustomerShop() != null) {
            dto.setShopCode(feedback.getCustomerShop().getShopCode());
            dto.setShopName(feedback.getCustomerShop().getShopName());
        }
        dto.setReason(feedback.getReason() != null ? feedback.getReason().name() : null);
        dto.setNote(feedback.getNote());
        dto.setDistanceFromShopMeters(feedback.getDistanceFromShopMeters());
        dto.setVisitDate(feedback.getVisitDate());
        dto.setVisitTime(feedback.getVisitTime());
        return dto;
    }
}
