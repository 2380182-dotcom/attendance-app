package com.dawnbread.attendance.entity;

import com.dawnbread.attendance.security.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;

/**
 * A sales-route customer shop — distinct from Mart (an agent's attendance
 * duty location). shopCode is what a salesman types in the mobile sales
 * flow (a later stage) to auto-fill everything below from what admin
 * registered here; geofence fields mirror Mart's shape but are enforced
 * with an admin-configurable buffer (see LmtSettings) rather than a bare
 * hard block, since a sales route visits many shops with imperfect GPS.
 */
@Entity
@Table(name = "customer_shops")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners({TenantEntityListener.class})
public class CustomerShop implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shop_code", nullable = false)
    private String shopCode;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    private String branch;
    private String address;
    private String phone;
    private String mobile;
    private String email;
    private String strn;
    private String ntn;

    @ManyToOne
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    private Double latitude;
    private Double longitude;
    private Double radius;

    @Column(name = "geo_fencing_enabled")
    private Boolean geoFencingEnabled = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public CustomerShop() {}

    // Getters
    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getShopCode() { return shopCode; }
    public String getShopName() { return shopName; }
    public String getBranch() { return branch; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getStrn() { return strn; }
    public String getNtn() { return ntn; }
    public Area getArea() { return area; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getRadius() { return radius; }
    public Boolean getGeoFencingEnabled() { return geoFencingEnabled; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public void setBranch(String branch) { this.branch = branch; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public void setEmail(String email) { this.email = email; }
    public void setStrn(String strn) { this.strn = strn; }
    public void setNtn(String ntn) { this.ntn = ntn; }
    public void setArea(Area area) { this.area = area; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setRadius(Double radius) { this.radius = radius; }
    public void setGeoFencingEnabled(Boolean geoFencingEnabled) { this.geoFencingEnabled = geoFencingEnabled; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
