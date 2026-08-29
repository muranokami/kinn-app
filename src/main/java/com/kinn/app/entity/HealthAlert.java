package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康アラート(一般的な注意喚起)。医療診断や病気の判定は行わない。
 * HealthAlertService が直近の記録を評価し、条件を満たした場合にのみ生成する。
 * 同じ日に同じ種別のアラートを重複生成しないよう、employeeId + alertType + triggeredDate
 * にユニーク制約を設けている。
 */
@Entity
@Table(
        name = "health_alert",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "alert_type", "triggered_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 32)
    private HealthAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private HealthAlertSeverity severity;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** この評価が対象とした基準日(通常は評価実行日) */
    @Column(name = "triggered_date", nullable = false)
    private LocalDate triggeredDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
