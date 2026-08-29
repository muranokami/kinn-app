package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 1件の予定。1日に複数件登録できる(勤怠・健康記録と違い、日付ごとの一意制約は設けない)。
 *
 * 個人スケジュールと部署共有スケジュールの両方をこのEntity1つで表現する(⑮ 既存Entityを
 * 拡張する方針。無理に新規Entityを作らない)。{@link #scheduleType} で区別し、

 * <ul>
 *   <li>PERSONAL(従来どおり): {@link #employeeId} が持ち主。department_id/company_id は null。</li>
 *   <li>DEPARTMENT(今回追加): {@link #departmentId}/{@link #companyId} が「誰が見られるか」を
 *       決める(部署に所属する全員が閲覧できる)。employeeId には登録した管理者の実効IDを
 *       監査目的で保持するが、可視性の判定には一切使わない。</li>
 * </ul>
 *
 * 会社単位・部署単位のアクセス制御(⑨⑩⑪⑫)は、department_id 単体ではなく必ず
 * company_id + department_id の組み合わせで行う(他社が偶然同じdepartment_idを持つ
 * ケースを排除するため。DepartmentScheduleService参照)。
 */
@Entity
@Table(name = "schedule_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** 終日予定の場合は null */
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    @Builder.Default
    private ScheduleCategory category = ScheduleCategory.OTHER;

    @Column(name = "memo", length = 255)
    private String memo;

    /**
     * PERSONAL/DEPARTMENTの区別(⑮)。既存データはこの列がまだ無い状態から移行するため
     * nullable(NOT NULL制約は付けない。ddl-auto=updateで既存行を壊さないため)。
     * NULLは起動時のScheduleTypeMigrationRunnerがPERSONALへ補完し、アプリ側の読み取りでも
     * 「nullはPERSONAL相当として扱う」既存クエリ(employeeIdのみで絞り込む個人用API)を
     * 一切変更しないことで後方互換を保つ。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", length = 32)
    @Builder.Default
    private ScheduleType scheduleType = ScheduleType.PERSONAL;

    /** DEPARTMENT種別の場合のみ設定。部署共有スケジュールの「誰が見られるか」の唯一の根拠 */
    @Column(name = "department_id")
    private Long departmentId;

    /**
     * DEPARTMENT種別の場合のみ設定。department_idだけでは他社の同IDと衝突しうるため、
     * 会社単位のアクセス制御(⑪)を二重に保証する目的で保持する。
     */
    @Column(name = "company_id")
    private Long companyId;

    /**
     * DEPARTMENT種別の場合、登録した管理者のAppUser.id。可視性の判定には使わない
     * (⑳「誰が登録したか」より「部署として何が予定されているか」を優先するUI方針)が、
     * 将来、登録権限を部署メンバーの一部/全員へ広げる際の下地として保持する(⑧)。
     */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /** 場所(任意)。部署共有スケジュールの登録項目として使う(⑦) */
    @Column(name = "location", length = 255)
    private String location;
}
