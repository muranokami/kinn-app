package com.kinn.app.service;

import com.kinn.app.dto.MonthScheduleDto;
import com.kinn.app.dto.ScheduleEventDto;
import com.kinn.app.dto.ScheduleSummaryDto;
import com.kinn.app.entity.ScheduleCategory;
import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.repository.ScheduleEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * スケジュールのCRUD・集計を担うサービス。
 * 勤怠・健康記録と異なり1日に複数件登録できるため、日別グリッドではなく
 * イベント一覧(+ID指定でのCRUD)という形をとる。
 */
@Service
public class ScheduleService {

    private final ScheduleEventRepository repository;

    public ScheduleService(ScheduleEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public MonthScheduleDto getMonth(String employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<ScheduleEvent> events = repository
                .findByEmployeeIdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(employeeId, from, to);

        List<ScheduleEventDto> dtoList = events.stream().map(this::toDto).toList();
        ScheduleSummaryDto summary = summarize(year, month, events);

        return MonthScheduleDto.builder()
                .year(year)
                .month(month)
                .events(dtoList)
                .summary(summary)
                .build();
    }

    @Transactional
    public ScheduleEventDto create(String employeeId, ScheduleEventDto dto) {
        validate(dto);
        ScheduleEvent entity = ScheduleEvent.builder()
                .employeeId(employeeId)
                .eventDate(dto.getEventDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .title(dto.getTitle())
                .category(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory())
                .memo(dto.getMemo())
                .location(dto.getLocation())
                .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public ScheduleEventDto update(String employeeId, Long id, ScheduleEventDto dto) {
        ScheduleEvent entity = findOwned(employeeId, id, "編集");
        validate(dto);
        entity.setEventDate(dto.getEventDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setTitle(dto.getTitle());
        entity.setCategory(dto.getCategory() == null ? ScheduleCategory.OTHER : dto.getCategory());
        entity.setMemo(dto.getMemo());
        entity.setLocation(dto.getLocation());
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(String employeeId, Long id) {
        ScheduleEvent entity = findOwned(employeeId, id, "削除");
        repository.delete(entity);
    }

    /**
     * 入力チェック(㉓)。日付・タイトルは必須、開始/終了時刻はどちらも入力されている場合のみ
     * 前後関係を確認する(終日予定や片方だけの入力を妨げないため)。
     * userId/companyId/departmentIdはDTOに存在しない(そもそも受け取らない)ため、
     * ここでチェックするまでもなく不正指定はできない(⑯⑰の「自動取得のみ」を型で保証)。
     */
    private void validate(ScheduleEventDto dto) {
        if (dto.getEventDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日付は必須です");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "タイトルは必須です");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null && dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "終了時間は開始時間より前にできません");
        }
    }

    /**
     * 所有者チェック(⑦⑰。非常に重要: 画面側だけでなく必ずここで確認する)。
     * employeeIdはAuthenticationから取得した値のみを受け取り、リクエストボディやURLパラメータからは
     * 一切受け取らないため、他人のIDを指定して呼び出す経路自体が存在しない。
     * @param action エラーメッセージに使う操作名("編集"/"削除")。㉔ 画面にわかりやすいエラーを出すため。
     */
    private ScheduleEvent findOwned(String employeeId, Long id, String action) {
        ScheduleEvent entity = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "このスケジュールは見つかりません。既に削除されている可能性があります。"));
        if (!entity.getEmployeeId().equals(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "このスケジュールを" + action + "する権限がありません。");
        }
        return entity;
    }

    // ------------------------------------------------------------------
    // 集計ロジック
    // ------------------------------------------------------------------

    private ScheduleSummaryDto summarize(int year, int month, List<ScheduleEvent> events) {
        int workCount = 0, meetingCount = 0, privateCount = 0, healthCount = 0, otherCount = 0;
        for (ScheduleEvent e : events) {
            switch (e.getCategory()) {
                case WORK -> workCount++;
                case MEETING -> meetingCount++;
                case PRIVATE -> privateCount++;
                case HEALTH -> healthCount++;
                case OTHER -> otherCount++;
            }
        }
        return ScheduleSummaryDto.builder()
                .year(year)
                .month(month)
                .totalEvents(events.size())
                .workCount(workCount)
                .meetingCount(meetingCount)
                .privateCount(privateCount)
                .healthCount(healthCount)
                .otherCount(otherCount)
                .build();
    }

    private ScheduleEventDto toDto(ScheduleEvent e) {
        return ScheduleEventDto.builder()
                .id(e.getId())
                .eventDate(e.getEventDate())
                .startTime(e.getStartTime())
                .endTime(e.getEndTime())
                .title(e.getTitle())
                .category(e.getCategory())
                .memo(e.getMemo())
                .location(e.getLocation())
                .build();
    }
}
