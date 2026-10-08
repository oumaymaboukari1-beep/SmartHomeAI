package com.smarthome.smarthome_backend.repository;

import com.smarthome.smarthome_backend.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ReadingRepository extends JpaRepository<Reading, Long> {
    List<Reading> findAllByDeviceUserEmailIgnoreCaseAndRecordedAtAfterOrderByRecordedAtAsc(String email, LocalDateTime from);
    List<Reading> findAllByDeviceIdAndDeviceUserEmailIgnoreCaseOrderByRecordedAtDesc(Long deviceId, String email);
}
