package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.*;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.entity.HierarchyPerson;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.CustomerShopService;
import jakarta.servlet.http.HttpServletRequest;
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
        return dto;
    }
}
