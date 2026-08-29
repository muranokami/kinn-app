package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 管理者による勤怠修正の監査ログ。1回の修正につき1行、修正前後の値をそのまま記録する
 * (この用途では「その時点の事実の記録」自体が目的のため、他のDTOのように都度計算せず、
 * AttendanceRecordのjavadocにある「集計値は保存しない」方針の対象外として扱う)。
 */
@Entity
@Table(name = "attendance_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 修正対象の従業員(実効employeeId="companyId|loginId") */
    @Column(name = "target_employee_id", nullable = false, length = 64)
    private String targetEmployeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 修正した管理者(実効employeeId) */
    @Column(name = "edited_by_employee_id", nullable = false, length = 64)
    private String editedByEmployeeId;

    @Column(name = "edited_by_name", length = 100)
    private String editedByName;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_day_type", length = 32)
    private DayType previousDayType;

    @Column(name = "previous_start_time")
    private LocalTime previousStartTime;

    @Column(name = "previous_end_time")
    private LocalTime previousEndTime;

    @Column(name = "previous_break_minutes")
    private Integer previousBreakMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_day_type", length = 32)
    private DayType newDayType;

    @Column(name = "new_start_time")
    private LocalTime newStartTime;

    @Column(name = "new_end_time")
    private LocalTime newEndTime;

    @Column(name = "new_break_minutes")
    private Integer newBreakMinutes;

    @PrePersist
    private void onCreate() {
        this.editedAt = LocalDateTime.now();
    }
}
