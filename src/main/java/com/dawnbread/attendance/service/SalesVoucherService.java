package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.VoucherDetailDTO;
import com.dawnbread.attendance.dto.VoucherListItemDTO;
import com.dawnbread.attendance.dto.VoucherShopSummaryDTO;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.SaleItem;
import com.dawnbread.attendance.entity.SalesRecord;
import com.dawnbread.attendance.repository.SalesRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Task 4: Local/LMT Sales Voucher sections. "Local" and "LMT" are decided
 * by the salesman's role (SALESMAN_LOCAL / SALESMAN_LMT), never the shop —
 * a shop carries no type of its own (CustomerShop.assignedAgent is a Task 3
 * LMT-only concept), and nothing stops the same shop being visited by both
 * kinds of salesman. Every query below reads SalesRecord.agentRoleAtSale
 * (snapshotted at submission time) falling back to the live agent.role join
 * only for records that predate that field, so a later role change on an
 * agent never moves their already-existing vouchers to the other section
 * (see SalesRecordRepository's COALESCE usage).
 *
 * Only vouchers with a customerShop are in scope here (the legacy /entry
 * flow's split-sale records have no shop and are untouched, as always).
 */
@Service
@Transactional(readOnly = true)
public class SalesVoucherService {

    public static final String ROLE_LOCAL = "SALESMAN_LOCAL";
    public static final String ROLE_LMT = "SALESMAN_LMT";

    @Autowired
    private SalesRecordRepository salesRecordRepository;

    public Page<VoucherShopSummaryDTO> getShopSummaries(String role, String shopSearch, Pageable pageable) {
        String normalizedSearch = (shopSearch == null || shopSearch.isBlank()) ? null : shopSearch.trim();
        return salesRecordRepository.findShopVoucherSummaries(role, normalizedSearch, pageable);
    }

    public Page<VoucherListItemDTO> getVouchersForShop(Long shopId, String role, Pageable pageable) {
        return salesRecordRepository.findVoucherListForShop(shopId, role, pageable);
    }

    /**
     * Empty means either the voucher doesn't exist, or it belongs to a
     * different tenant (the Hibernate tenantFilter, enabled per-request by
     * SecurityInterceptor for every standard JPQL query, transparently
     * excludes it) — the caller maps both to 404, never 403, so a cross-
     * tenant probe can't distinguish "not found" from "not yours."
     */
    public Optional<VoucherDetailDTO> getVoucherDetail(Long voucherId) {
        return salesRecordRepository.findDetailById(voucherId).map(SalesVoucherService::toDetailDTO);
    }

    private static VoucherDetailDTO toDetailDTO(SalesRecord sr) {
        VoucherDetailDTO dto = new VoucherDetailDTO();
        dto.setVoucherId(sr.getId());
        CustomerShop shop = sr.getCustomerShop();
        if (shop != null) {
            dto.setShopCode(shop.getShopCode());
            dto.setShopName(shop.getShopName());
            dto.setBranch(shop.getBranch());
        }
        if (sr.getAgent() != null) {
            dto.setAgentName(sr.getAgent().getName());
        }
        dto.setAgentRole(sr.getAgentRoleAtSale() != null ? sr.getAgentRoleAtSale()
                : (sr.getAgent() != null ? sr.getAgent().getRole() : null));
        dto.setSaleDate(sr.getSaleDate());
        dto.setSaleTime(sr.getSaleTime());
        dto.setDistanceFromShopMeters(sr.getDistanceFromShopMeters());
        dto.setStatus(sr.getStatus());
        dto.setRequestId(sr.getRequestId());
        dto.setItems(sr.getItems().stream().map(SalesVoucherService::toItem).collect(Collectors.toList()));
        return dto;
    }

    private static VoucherDetailDTO.Item toItem(SaleItem item) {
        return new VoucherDetailDTO.Item(
                item.getProduct() != null ? item.getProduct().getName() : null,
                item.getTransactionType() != null ? item.getTransactionType().name() : null,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscountPercent());
    }
}
