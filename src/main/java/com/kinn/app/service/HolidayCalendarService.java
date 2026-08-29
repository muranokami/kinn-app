package com.kinn.app.service;

import com.kinn.app.dto.HolidayDto;
import com.kinn.app.dto.HolidayOverrideDto;
import com.kinn.app.entity.HolidayOverride;
import com.kinn.app.repository.HolidayOverrideRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 日本の祝日カレンダーを提供するサービス。
 * {@link JapaneseHolidayCalculator} の算出結果に、{@code holiday_override} テーブルの
 * 手動オーバーライドを重ねて最終判定する(オーバーライドが常に優先)。
 */
@Service
public class HolidayCalendarService {

    private final HolidayOverrideRepository overrideRepository;

    public HolidayCalendarService(HolidayOverrideRepository overrideRepository) {
        this.overrideRepository = overrideRepository;
    }

    /** 指定日が祝日であれば祝日名を返す(祝日でなければ空) */
    @Transactional(readOnly = true)
    public Optional<String> getHolidayName(LocalDate date) {
        Optional<HolidayOverride> override = overrideRepository.findByDate(date);
        if (override.isPresent()) {
            HolidayOverride o = override.get();
            return o.isHoliday() ? Optional.of(o.getName() == null || o.getName().isBlank() ? "祝日" : o.getName())
                    : Optional.empty();
        }
        String calculated = JapaneseHolidayCalculator.holidaysOf(date.getYear()).get(date);
        return Optional.ofNullable(calculated);
    }

    public boolean isPublicHoliday(LocalDate date) {
        return getHolidayName(date).isPresent();
    }

    /** 指定年の祝日一覧(カレンダー表示・CSV出力用)。オーバーライドを反映した最終結果を返す */
    @Transactional(readOnly = true)
    public List<HolidayDto> getHolidaysOfYear(int year) {
        Map<LocalDate, String> merged = new TreeMap<>(JapaneseHolidayCalculator.holidaysOf(year));

        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        for (HolidayOverride o : overrideRepository.findByDateBetweenOrderByDateAsc(from, to)) {
            if (o.isHoliday()) {
                merged.put(o.getDate(), o.getName() == null || o.getName().isBlank() ? "祝日" : o.getName());
            } else {
                merged.remove(o.getDate());
            }
        }

        return merged.entrySet().stream()
                .map(e -> HolidayDto.builder().date(e.getKey()).name(e.getValue()).build())
                .toList();
    }

    // ------------------------------------------------------------------
    // オーバーライドの管理(将来的に祝日カレンダーを更新するための拡張ポイント)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<HolidayOverrideDto> getOverrides() {
        return overrideRepository.findAll().stream()
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public HolidayOverrideDto saveOverride(HolidayOverrideDto dto) {
        HolidayOverride entity = overrideRepository.findByDate(dto.getDate()).orElseGet(HolidayOverride::new);
        entity.setDate(dto.getDate());
        entity.setName(dto.getName());
        entity.setHoliday(Boolean.TRUE.equals(dto.getHoliday()));
        return toDto(overrideRepository.save(entity));
    }

    @Transactional
    public void deleteOverride(Long id) {
        if (!overrideRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のオーバーライドが見つかりません");
        }
        overrideRepository.deleteById(id);
    }

    private HolidayOverrideDto toDto(HolidayOverride o) {
        return HolidayOverrideDto.builder()
                .id(o.getId())
                .date(o.getDate())
                .name(o.getName())
                .holiday(o.isHoliday())
                .build();
    }
}
