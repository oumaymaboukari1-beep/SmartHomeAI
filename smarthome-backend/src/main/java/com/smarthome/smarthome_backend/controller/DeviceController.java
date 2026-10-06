package com.smarthome.smarthome_backend.controller;

import com.smarthome.smarthome_backend.entity.Device;
import com.smarthome.smarthome_backend.repository.DeviceRepository;
import com.smarthome.smarthome_backend.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceRepository devices;
    private final UserRepository users;

    public DeviceController(DeviceRepository devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    @GetMapping
    public List<DeviceResponse> list(Authentication authentication) {
        return devices.findAllByUserEmailIgnoreCaseOrderByIdDesc(authentication.getName()).stream()
                .map(DeviceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse create(@Valid @RequestBody DeviceRequest request, Authentication authentication) {
        var device = new Device();
        apply(device, request);
        device.setUser(users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")));
        return DeviceResponse.from(devices.save(device));
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request,
                                 Authentication authentication) {
        var device = findOwned(id, authentication.getName());
        apply(device, request);
        return DeviceResponse.from(devices.save(device));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        devices.delete(findOwned(id, authentication.getName()));
    }

    private Device findOwned(Long id, String email) {
        return devices.findByIdAndUserEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    private void apply(Device device, DeviceRequest request) {
        device.setName(request.name().trim());
        device.setType(request.type().trim());
        device.setLocation(request.location() == null ? null : request.location().trim());
        device.setActive(request.active());
    }

    public record DeviceRequest(@NotBlank @Size(max = 100) String name,
                                @NotBlank @Size(max = 80) String type,
                                @Size(max = 120) String location,
                                boolean active) {}
    public record DeviceResponse(Long id, String name, String type, String location, boolean active) {
        static DeviceResponse from(Device device) {
            return new DeviceResponse(device.getId(), device.getName(), device.getType(),
                    device.getLocation(), device.isActive());
        }
    }
}
