package com.dawnbread.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Mart {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double radius;
    private Boolean geoFencingEnabled = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "mart", cascade = CascadeType.ALL)
    private List<Attendance> attendances = new ArrayList<>();
    
    // Constructors
    public Mart() {}
    
    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getRadius() { return radius; }
    public Boolean getGeoFencingEnabled() { return geoFencingEnabled; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Attendance> getAttendances() { return attendances; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setRadius(Double radius) { this.radius = radius; }
    public void setGeoFencingEnabled(Boolean geoFencingEnabled) { this.geoFencingEnabled = geoFencingEnabled; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setAttendances(List<Attendance> attendances) { this.attendances = attendances; }
}
