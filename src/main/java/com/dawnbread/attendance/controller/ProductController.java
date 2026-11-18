package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.entity.ProductFamily;
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

    /**
     * Body: {"active": true|false}. ADMIN only — Sales sees the On/Hidden
     * status on the pricing list but cannot switch it. Switching on needs
     * both main prices entered.
     */
    @PutMapping("/{id}/active")
    public ResponseEntity<ApiResponse<ProductPricingDTO>> setActive(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can switch a product on or off."));
        }
        if (!(body.get("active") instanceof Boolean active)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("active must be true or false"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(active ? "Product switched on" : "Product hidden",
                    productService.setActive(id, active)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Body: {"familyId": 3 | null, "sortOrder": 14 | null, "onPriceList": true|false}.
     * ADMIN only: the product's place on the official price list (every
     * product list follows sortOrder; the app's filter buttons follow familyId).
     */
    @PutMapping("/{id}/placement")
    public ResponseEntity<ApiResponse<ProductPricingDTO>> setPlacement(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        Object family = body.get("familyId"), order = body.get("sortOrder");
        if ((family != null && !(family instanceof Number)) || (order != null && !(order instanceof Number))
                || !(body.get("onPriceList") instanceof Boolean onList)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "familyId and sortOrder must be numbers or empty, onPriceList true or false"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("Product placement saved", productService.setPlacement(id,
                    family == null ? null : ((Number) family).longValue(),
                    order == null ? null : ((Number) order).intValue(), onList)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Body: {"name": "Bread", "nameUr": "..." | null, "sortOrder": 1}. ADMIN only. */
    @PostMapping("/families")
    public ResponseEntity<ApiResponse<ProductFamily>> addFamily(@RequestBody java.util.Map<String, Object> body) {
        return saveFamily(null, body);
    }

    @PutMapping("/families/{familyId}")
    public ResponseEntity<ApiResponse<ProductFamily>> updateFamily(@PathVariable Long familyId, @RequestBody java.util.Map<String, Object> body) {
        return saveFamily(familyId, body);
    }

    /** Only an empty family can be deleted. ADMIN only. */
    @DeleteMapping("/families/{familyId}")
    public ResponseEntity<ApiResponse<Void>> deleteFamily(@PathVariable Long familyId) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            productService.deleteFamily(familyId);
            return ResponseEntity.ok(ApiResponse.success("Family deleted", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<ProductFamily>> saveFamily(Long familyId, java.util.Map<String, Object> body) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        Object name = body.get("name"), nameUr = body.get("nameUr"), order = body.get("sortOrder");
        if (!(name instanceof String) || (nameUr != null && !(nameUr instanceof String)) || !(order instanceof Number)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("name and sortOrder are needed; nameUr is text or empty"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(familyId == null ? "Family added" : "Family saved",
                    productService.saveFamily(familyId, (String) name, (String) nameUr, ((Number) order).intValue())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can change product families and the list order."));
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
