package com.smarthome.smarthome_backend.repository;

import com.smarthome.smarthome_backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByUserEmailIgnoreCaseOrderByIdDesc(String email);
    Optional<Device> findByIdAndUserEmailIgnoreCase(Long id, String email);
}
