package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.entity.ShopProductDiscount;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.CustomerShopService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** Customer master data (LMT). Admin-only for writes, same shape as MartController. */
@RestController
@RequestMapping("/api/lmt/customer-shops")
public class CustomerShopController {

    @Autowired
    private CustomerShopService customerShopService;

    @Autowired
    private HttpServletRequest request;

    private ResponseEntity<ApiResponse<CustomerShopDTO>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only an administrator can manage customer shops."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerShopDTO>> create(@RequestBody CustomerShopCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            CustomerShop created = customerShopService.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Customer shop created successfully", convertToDTO(created)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerShopDTO>>> getActive() {
        List<CustomerShopDTO> dtos = customerShopService.getActive().stream()
                .map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Customer shops retrieved successfully", dtos));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CustomerShopDTO>>> getAll() {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can view inactive customer shops."));
        }
        List<CustomerShopDTO> dtos = customerShopService.getAll().stream()
                .map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All customer shops retrieved successfully", dtos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerShopDTO>> getById(@PathVariable Long id) {
        return customerShopService.getById(id)
                .map(s -> ResponseEntity.ok(ApiResponse.success("Customer shop found", convertToDTO(s))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Customer shop not found with id: " + id)));
    }

    /** Not used by anything yet — reserved for the mobile "enter shop code" flow (a later stage). */
    @GetMapping("/code/{shopCode}")
    public ResponseEntity<ApiResponse<CustomerShopDTO>> getByShopCode(@PathVariable String shopCode) {
        return customerShopService.getByShopCode(shopCode)
                .map(s -> ResponseEntity.ok(ApiResponse.success("Customer shop found", convertToDTO(s))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No customer shop registered with code: " + shopCode)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CustomerShopDTO>>> search(@RequestParam String shopName) {
        List<CustomerShopDTO> dtos = customerShopService.searchByName(shopName).stream()
                .map(this::convertToDTO).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Customer shops found", dtos));
    }

    /**
     * The LMT "show nearby shops" flow — read-only convenience filter, same
     * unguarded-read convention as /code/{shopCode} and /search above. The
     * real authority stays SalesService.submitShopVisit's own independent
     * geofence check at submit time; this endpoint never issues anything
     * that check trusts.
     */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<CustomerShopDTO>>> getNearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        List<CustomerShopDTO> dtos = customerShopService.getNearby(latitude, longitude).stream()
                .map(nearby -> {
                    CustomerShopDTO dto = convertToDTO(nearby.getShop());
                    dto.setDistanceMeters(nearby.getDistanceMeters());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Nearby customer shops retrieved successfully", dtos));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerShopDTO>> update(@PathVariable Long id, @RequestBody CustomerShopCreateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            CustomerShop updated = customerShopService.update(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Customer shop updated successfully", convertToDTO(updated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage customer shops."));
        }
        try {
            customerShopService.deactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Customer shop deactivated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<CustomerShopDTO>> reactivate(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return adminOnly();
        }
        try {
            CustomerShop reactivated = customerShopService.reactivate(id);
            return ResponseEntity.ok(ApiResponse.success("Customer shop reactivated successfully", convertToDTO(reactivated)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * SKU discount overrides for one shop — unguarded read, same convention
     * as /nearby and /code/{shopCode}: both the admin shop-edit dialog and
     * the mobile discounted-total preview (Feature 2, P6) need this.
     */
    @GetMapping("/{id}/product-discounts")
    public ResponseEntity<ApiResponse<List<ShopProductDiscountDTO>>> getProductDiscounts(@PathVariable Long id) {
        try {
            List<ShopProductDiscountDTO> dtos = customerShopService.getProductDiscounts(id).stream()
                    .map(this::convertDiscountToDTO).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Shop product discounts retrieved successfully", dtos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}/product-discounts/{productId}")
    public ResponseEntity<ApiResponse<ShopProductDiscountDTO>> upsertProductDiscount(
            @PathVariable Long id, @PathVariable Long productId, @Valid @RequestBody ShopProductDiscountUpdateDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage shop discounts."));
        }
        try {
            ShopProductDiscount saved = customerShopService.upsertProductDiscount(id, productId, dto.getDiscountPercent());
            return ResponseEntity.ok(ApiResponse.success("Shop product discount saved successfully", convertDiscountToDTO(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/product-discounts/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeProductDiscount(@PathVariable Long id, @PathVariable Long productId) {
        if (!AccessControl.hasRole(request, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only an administrator can manage shop discounts."));
        }
        try {
            customerShopService.removeProductDiscount(id, productId);
            return ResponseEntity.ok(ApiResponse.success("Shop product discount removed successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private ShopProductDiscountDTO convertDiscountToDTO(ShopProductDiscount discount) {
        return new ShopProductDiscountDTO(
                discount.getProduct().getId(),
                discount.getProduct().getName(),
                discount.getDiscountPercent()
        );
    }

    private HierarchyPersonDTO convertPersonToDTO(HierarchyPerson person) {
        if (person == null) {
            return null;
        }
        HierarchyPersonDTO dto = new HierarchyPersonDTO(
                person.getId(), person.getName(), person.getRoleLabel(), person.getContact(), person.getCreatedAt()
        );
        dto.setIsActive(person.getIsActive());
        return dto;
    }

    private AreaDTO convertAreaToDTO(Area area) {
        if (area == null) {
            return null;
        }
        AreaDTO dto = new AreaDTO(area.getId(), area.getName(), area.getCreatedAt());
        dto.setTse(convertPersonToDTO(area.getTse()));
        dto.setSrTse(convertPersonToDTO(area.getSrTse()));
        dto.setAsm(convertPersonToDTO(area.getAsm()));
        dto.setIsActive(area.getIsActive());
        return dto;
    }

    private CustomerShopDTO convertToDTO(CustomerShop shop) {
        CustomerShopDTO dto = new CustomerShopDTO();
        dto.setId(shop.getId());
        dto.setShopCode(shop.getShopCode());
        dto.setShopName(shop.getShopName());
        dto.setBranch(shop.getBranch());
        dto.setAddress(shop.getAddress());
        dto.setPhone(shop.getPhone());
        dto.setMobile(shop.getMobile());
        dto.setEmail(shop.getEmail());
        dto.setStrn(shop.getStrn());
        dto.setNtn(shop.getNtn());
        dto.setArea(convertAreaToDTO(shop.getArea()));
        dto.setLatitude(shop.getLatitude());
        dto.setLongitude(shop.getLongitude());
        dto.setRadius(shop.getRadius());
        dto.setGeoFencingEnabled(shop.getGeoFencingEnabled());
        dto.setIsActive(shop.getIsActive());
        dto.setCreatedAt(shop.getCreatedAt());
        dto.setDiscountPercent(shop.getDiscountPercent());
        return dto;
    }
}
