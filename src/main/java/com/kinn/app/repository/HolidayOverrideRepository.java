package com.kinn.app.repository;

import com.kinn.app.entity.HolidayOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HolidayOverrideRepository extends JpaRepository<HolidayOverride, Long> {

    Optional<HolidayOverride> findByDate(LocalDate date);

    List<HolidayOverride> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
