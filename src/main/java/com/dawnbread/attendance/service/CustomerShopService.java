package com.dawnbread.attendance.service;

import com.dawnbread.attendance.dto.CustomerShopCreateDTO;
import com.dawnbread.attendance.entity.Area;
import com.dawnbread.attendance.entity.CustomerShop;
import com.dawnbread.attendance.repository.AreaRepository;
import com.dawnbread.attendance.repository.CustomerShopRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CustomerShopService {

    @Autowired
    private CustomerShopRepository customerShopRepository;

    @Autowired
    private AreaRepository areaRepository;

    private Area resolveArea(Long areaId) {
        return areaRepository.findById(areaId)
                .orElseThrow(() -> new RuntimeException("Area not found with id: " + areaId));
    }

    public CustomerShop create(CustomerShopCreateDTO dto) {
        if (customerShopRepository.existsByShopCode(dto.getShopCode())) {
            throw new RuntimeException("Shop code already exists: " + dto.getShopCode());
        }
        CustomerShop shop = new CustomerShop();
        shop.setShopCode(dto.getShopCode());
        shop.setShopName(dto.getShopName());
        shop.setBranch(dto.getBranch());
        shop.setAddress(dto.getAddress());
        shop.setPhone(dto.getPhone());
        shop.setMobile(dto.getMobile());
        shop.setEmail(dto.getEmail());
        shop.setStrn(dto.getStrn());
        shop.setNtn(dto.getNtn());
        shop.setArea(resolveArea(dto.getAreaId()));
        shop.setLatitude(dto.getLatitude());
        shop.setLongitude(dto.getLongitude());
        shop.setRadius(dto.getRadius());
        if (dto.getGeoFencingEnabled() != null) {
            shop.setGeoFencingEnabled(dto.getGeoFencingEnabled());
        }
        shop.setCreatedAt(LocalDateTime.now());
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }

    public List<CustomerShop> getActive() {
        return customerShopRepository.findByIsActiveTrue();
    }

    public List<CustomerShop> getAll() {
        return customerShopRepository.findAll();
    }

    public Optional<CustomerShop> getById(Long id) {
        return customerShopRepository.findById(id);
    }

    /** The lookup the mobile "enter shop code" flow will use in a later stage. */
    public Optional<CustomerShop> getByShopCode(String shopCode) {
        return customerShopRepository.findByShopCode(shopCode);
    }

    public List<CustomerShop> searchByName(String shopName) {
        return customerShopRepository.findByShopNameContainingIgnoreCase(shopName);
    }

    public CustomerShop update(Long id, CustomerShopCreateDTO dto) {
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));

        if (dto.getShopCode() != null) {
            Optional<CustomerShop> existing = customerShopRepository.findByShopCode(dto.getShopCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new RuntimeException("Shop code already taken: " + dto.getShopCode());
            }
            shop.setShopCode(dto.getShopCode());
        }
        if (dto.getShopName() != null) {
            shop.setShopName(dto.getShopName());
        }
        if (dto.getBranch() != null) {
            shop.setBranch(dto.getBranch());
        }
        if (dto.getAddress() != null) {
            shop.setAddress(dto.getAddress());
        }
        if (dto.getPhone() != null) {
            shop.setPhone(dto.getPhone());
        }
        if (dto.getMobile() != null) {
            shop.setMobile(dto.getMobile());
        }
        if (dto.getEmail() != null) {
            shop.setEmail(dto.getEmail());
        }
        if (dto.getStrn() != null) {
            shop.setStrn(dto.getStrn());
        }
        if (dto.getNtn() != null) {
            shop.setNtn(dto.getNtn());
        }
        if (dto.getAreaId() != null) {
            shop.setArea(resolveArea(dto.getAreaId()));
        }
        if (dto.getLatitude() != null) {
            shop.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            shop.setLongitude(dto.getLongitude());
        }
        if (dto.getRadius() != null) {
            shop.setRadius(dto.getRadius());
        }
        if (dto.getGeoFencingEnabled() != null) {
            shop.setGeoFencingEnabled(dto.getGeoFencingEnabled());
        }
        return customerShopRepository.save(shop);
    }

    /**
     * Soft-delete only — matches Mart's rationale exactly: once any sales
     * activity references a shop (a later stage), a hard delete would
     * either fail or destroy historical records.
     */
    public void deactivate(Long id) {
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));
        shop.setIsActive(false);
        customerShopRepository.save(shop);
    }

    public CustomerShop reactivate(Long id) {
        CustomerShop shop = customerShopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer shop not found with id: " + id));
        shop.setIsActive(true);
        return customerShopRepository.save(shop);
    }
}
