package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.ProductPricingDTO;
import com.dawnbread.attendance.dto.ProductPricingUpdateDTO;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role-based pricing (P3): ADMIN + SALES manage product pricing from the
 * Sales Dashboard — prices are set from there, so SALES manages them, not
 * just ADMIN. Distinct from /api/sales/products (the read-only catalog
 * every mobile app uses for display) — this is the write surface.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final String[] PRICING_ROLES = { "ADMIN", "SALES" };

    @Autowired
    private ProductService productService;

    @Autowired
    private HttpServletRequest request;

    private <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Admin or Sales can manage product pricing."));
    }

    @GetMapping("/pricing")
    public ResponseEntity<ApiResponse<List<ProductPricingDTO>>> getPricing() {
        if (!AccessControl.hasRole(request, PRICING_ROLES)) {
            return forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success("Product pricing retrieved", productService.getAllForPricing()));
    }

    @PutMapping("/{id}/pricing")
    public ResponseEntity<ApiResponse<ProductPricingDTO>> updatePricing(
            @PathVariable Long id, @Valid @RequestBody ProductPricingUpdateDTO dto) {
        if (!AccessControl.hasRole(request, PRICING_ROLES)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Product pricing updated", productService.updatePricing(id, dto)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
