package com.dawnbread.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_sync_log")
public class SalesSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_record_id")
    private Long saleRecordId;

    @Column(name = "synced_to", length = 50)
    private String syncedTo;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "sync_status", length = 20)
    private String syncStatus;

    @Column(name = "sync_message", columnDefinition = "TEXT")
    private String syncMessage;

    public SalesSyncLog() {}

    public SalesSyncLog(Long saleRecordId, String syncedTo, LocalDateTime syncedAt, String syncStatus, String syncMessage) {
        this.saleRecordId = saleRecordId;
        this.syncedTo = syncedTo;
        this.syncedAt = syncedAt;
        this.syncStatus = syncStatus;
        this.syncMessage = syncMessage;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSaleRecordId() { return saleRecordId; }
    public void setSaleRecordId(Long saleRecordId) { this.saleRecordId = saleRecordId; }

    public String getSyncedTo() { return syncedTo; }
    public void setSyncedTo(String syncedTo) { this.syncedTo = syncedTo; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

    public String getSyncMessage() { return syncMessage; }
    public void setSyncMessage(String syncMessage) { this.syncMessage = syncMessage; }
}
