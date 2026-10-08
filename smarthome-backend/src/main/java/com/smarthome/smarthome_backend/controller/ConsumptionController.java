package com.smarthome.smarthome_backend.controller;

import com.smarthome.smarthome_backend.entity.Reading;
import com.smarthome.smarthome_backend.entity.Alert;
import com.smarthome.smarthome_backend.repository.AlertRepository;
import com.smarthome.smarthome_backend.repository.DeviceRepository;
import com.smarthome.smarthome_backend.repository.ReadingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ConsumptionController {
    private final DeviceRepository devices;
    private final ReadingRepository readings;
    private final AlertRepository alerts;

    public ConsumptionController(DeviceRepository devices, ReadingRepository readings, AlertRepository alerts) {
        this.devices = devices;
        this.readings = readings;
        this.alerts = alerts;
    }

    @GetMapping("/devices/{deviceId}/readings")
    public List<ReadingResponse> list(@PathVariable Long deviceId, Authentication authentication) {
        requireOwnedDevice(deviceId, authentication.getName());
        return readings.findAllByDeviceIdAndDeviceUserEmailIgnoreCaseOrderByRecordedAtDesc(deviceId, authentication.getName())
                .stream().map(ReadingResponse::from).toList();
    }

    @PostMapping("/devices/{deviceId}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public ReadingResponse create(@PathVariable Long deviceId, @Valid @RequestBody ReadingRequest request,
                                  Authentication authentication) {
        var reading = new Reading();
        reading.setDevice(requireOwnedDevice(deviceId, authentication.getName()));
        reading.setConsumptionKwh(request.consumptionKwh());
        reading.setRecordedAt(request.recordedAt() == null ? LocalDateTime.now() : request.recordedAt());
        var recent = readings.findAllByDeviceIdAndDeviceUserEmailIgnoreCaseOrderByRecordedAtDesc(
                deviceId, authentication.getName()).stream()
                .filter(item -> item.getRecordedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .toList();
        var saved = readings.save(reading);
        if (recent.size() >= 5) {
            double average = recent.stream().mapToDouble(Reading::getConsumptionKwh).average().orElse(0);
            if (average > 0 && saved.getConsumptionKwh() >= average * 1.5) {
                var alert = new Alert();
                alert.setUser(saved.getDevice().getUser());
                alert.setSeverity("WARNING");
                alert.setMessage("Consommation inhabituelle détectée pour " + saved.getDevice().getName());
                alerts.save(alert);
            }
        }
        return ReadingResponse.from(saved);
    }

    @GetMapping("/consumption")
    public List<ReadingResponse> history(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            Authentication authentication) {
        if (days < 1 || days > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 366");
        }
        var start = from == null ? LocalDateTime.now().minusDays(days) : from.atStartOfDay();
        return readings.findAllByDeviceUserEmailIgnoreCaseAndRecordedAtAfterOrderByRecordedAtAsc(
                        authentication.getName(), start)
                .stream().map(ReadingResponse::from).toList();
    }

    private com.smarthome.smarthome_backend.entity.Device requireOwnedDevice(Long id, String email) {
        return devices.findByIdAndUserEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    public record ReadingRequest(@NotNull @DecimalMin("0.0") Double consumptionKwh,
                                 @PastOrPresent LocalDateTime recordedAt) {}
    public record ReadingResponse(Long id, Long deviceId, String deviceName, double consumptionKwh,
                                  LocalDateTime recordedAt) {
        static ReadingResponse from(Reading reading) {
            return new ReadingResponse(reading.getId(), reading.getDevice().getId(), reading.getDevice().getName(),
                    reading.getConsumptionKwh(), reading.getRecordedAt());
        }
    }
}
