package com.dawnbread.attendance.dto;

/** Input-only shape for POST /api/lmt/customer-shops. No id, no tenantId. */
public class CustomerShopCreateDTO {
    private String shopCode;
    private String shopName;
    private String branch;
    private String address;
    private String phone;
    private String mobile;
    private String email;
    private String strn;
    private String ntn;
    private Long areaId;
    private Double latitude;
    private Double longitude;
    private Double radius;
    private Boolean geoFencingEnabled;

    public CustomerShopCreateDTO() {}

    public String getShopCode() { return shopCode; }
    public void setShopCode(String shopCode) { this.shopCode = shopCode; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStrn() { return strn; }
    public void setStrn(String strn) { this.strn = strn; }

    public String getNtn() { return ntn; }
    public void setNtn(String ntn) { this.ntn = ntn; }

    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getRadius() { return radius; }
    public void setRadius(Double radius) { this.radius = radius; }

    public Boolean getGeoFencingEnabled() { return geoFencingEnabled; }
    public void setGeoFencingEnabled(Boolean geoFencingEnabled) { this.geoFencingEnabled = geoFencingEnabled; }
}
