package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.PageResponse;
import com.dawnbread.attendance.dto.VoucherDetailDTO;
import com.dawnbread.attendance.dto.VoucherListItemDTO;
import com.dawnbread.attendance.dto.VoucherShopSummaryDTO;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.SalesVoucherService;
import com.dawnbread.attendance.service.VoucherPdfService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 4: "Local Sales Vouchers" / "LMT Sales Vouchers" admin sections —
 * shop list (with DB-computed totals) -> a shop's voucher list -> a single
 * voucher's detail/PDF. Admin-only, same as every other Task 3/4 reporting
 * endpoint. Old pre-Task-2 split vouchers have no customerShop and never
 * appear here — untouched, as required.
 */
@RestController
@RequestMapping("/api/sales/vouchers")
public class SalesVoucherController {

    private static final String[] MANAGEMENT_ROLES = { "ADMIN", "SALES" };
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    private SalesVoucherService salesVoucherService;

    @Autowired
    private VoucherPdfService voucherPdfService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> managementOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can view sales vouchers."));
    }

    private Pageable pageable(int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), boundedSize);
    }

    private static String roleFor(String section) {
        if ("local".equalsIgnoreCase(section)) return SalesVoucherService.ROLE_LOCAL;
        if ("lmt".equalsIgnoreCase(section)) return SalesVoucherService.ROLE_LMT;
        return null;
    }

    /** GET /api/sales/vouchers/{section}/shops — section is "local" or "lmt". */
    @GetMapping("/{section}/shops")
    public ResponseEntity<ApiResponse<PageResponse<VoucherShopSummaryDTO>>> getShops(
            @PathVariable String section,
            @RequestParam(required = false) String shopSearch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        String role = roleFor(section);
        if (role == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("section must be 'local' or 'lmt'"));
        }
        var result = salesVoucherService.getShopSummaries(role, shopSearch, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success("Shop voucher summaries retrieved successfully", PageResponse.of(result)));
    }

    /** GET /api/sales/vouchers/{section}/shops/{shopId} — a shop's paginated voucher list. */
    @GetMapping("/{section}/shops/{shopId}")
    public ResponseEntity<ApiResponse<PageResponse<VoucherListItemDTO>>> getVouchersForShop(
            @PathVariable String section,
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        String role = roleFor(section);
        if (role == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("section must be 'local' or 'lmt'"));
        }
        var result = salesVoucherService.getVouchersForShop(shopId, role, pageable(page, size));
        return ResponseEntity.ok(ApiResponse.success("Vouchers retrieved successfully", PageResponse.of(result)));
    }

    /**
     * GET /api/sales/vouchers/{voucherId} — single voucher detail.
     * 404 (never 403) when the voucher doesn't exist OR belongs to
     * another tenant — the Hibernate tenantFilter already makes both cases
     * indistinguishable at the query level, so this simply reflects that.
     */
    @GetMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherDetailDTO>> getVoucherDetail(@PathVariable Long voucherId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return managementOnly();
        }
        return salesVoucherService.getVoucherDetail(voucherId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Voucher retrieved successfully", dto)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Voucher not found")));
    }

    /** GET /api/sales/vouchers/{voucherId}/pdf — same tenant-safe 404 as the detail endpoint above. */
    @GetMapping(value = "/{voucherId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getVoucherPdf(@PathVariable Long voucherId) {
        if (!AccessControl.hasRole(request, MANAGEMENT_ROLES)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return salesVoucherService.getVoucherDetail(voucherId)
                .map(dto -> {
                    byte[] pdf = voucherPdfService.generate(dto);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"voucher-" + voucherId + ".pdf\"")
                            .body(pdf);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
