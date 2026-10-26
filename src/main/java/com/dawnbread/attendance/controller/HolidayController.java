package com.dawnbread.attendance.controller;

import com.dawnbread.attendance.dto.ApiResponse;
import com.dawnbread.attendance.dto.HolidayDTO;
import com.dawnbread.attendance.security.AccessControl;
import com.dawnbread.attendance.service.HolidayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company holidays. Admin adds/edits/deletes; HR can read them (HR history
 * shows why nobody was marked absent on a holiday). Per company via the
 * tenant filter + explicit tenant checks in HolidayService.
 */
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    @Autowired
    private HttpServletRequest request;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HolidayDTO>>> list() {
        if (!AccessControl.hasRole(request, "ADMIN", "HR")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Only Admin and HR can view holidays."));
        }
        return ResponseEntity.ok(ApiResponse.success("Holidays retrieved", holidayService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HolidayDTO>> create(@RequestBody HolidayDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) return adminOnly();
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Holiday added", holidayService.create(dto)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HolidayDTO>> update(@PathVariable Long id, @RequestBody HolidayDTO dto) {
        if (!AccessControl.hasRole(request, "ADMIN")) return adminOnly();
        try {
            return ResponseEntity.ok(ApiResponse.success("Holiday updated", holidayService.update(id, dto)));
        } catch (HolidayService.HolidayNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Holiday not found."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!AccessControl.hasRole(request, "ADMIN")) return adminOnly();
        try {
            holidayService.delete(id);
            return ResponseEntity.ok(ApiResponse.success("Holiday deleted", null));
        } catch (HolidayService.HolidayNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Holiday not found."));
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> adminOnly() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Only an administrator can change holidays."));
    }
}
