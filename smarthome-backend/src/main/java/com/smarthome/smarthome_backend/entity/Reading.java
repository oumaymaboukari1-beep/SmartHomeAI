package com.smarthome.smarthome_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "readings", indexes = @Index(name = "idx_reading_device_time", columnList = "device_id, recorded_at"))
public class Reading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private double consumptionKwh;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public double getConsumptionKwh() { return consumptionKwh; }
    public void setConsumptionKwh(double consumptionKwh) { this.consumptionKwh = consumptionKwh; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
