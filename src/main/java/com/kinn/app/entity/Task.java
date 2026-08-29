package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * タスク管理機能(㉙)のEntity。
 *
 * Company → Department → AppUser → Task という既存の階層(㉑)にそのまま従い、
 * @ManyToOne は使わずFKカラム(companyId/departmentId/assignedUserId/createdByUserId)のみで
 * 参照する(他のEntityと同じ設計方針。Department/ScheduleEventのJavadoc参照)。
 *
 * companyId・departmentId・assignedUserId は必ずTaskService側で
 * 「ログインユーザー自身の会社・部署・本人」から解決するか、管理者の場合のみ
 * DepartmentService#requireOwned / AppUserRepository#findByIdAndCompanyId で
 * 「自社に属することを確認済みの値」だけを設定する(㉑㊳。URLやリクエストボディの数値IDを
 * 書き換えるだけでは他社・他部署のタスクを作成・参照・編集・削除できない)。
 *
 * 将来のスケジュール連携(㉔)・勤怠連携(㉕)を見据え、ScheduleEvent/AttendanceRecordとは
 * あえてテーブルを分けている(㉔の指示どおり、無理に同じテーブルへ統合しない)。
 * dueDate を軸にスケジュール側から参照する、assignedUserId(=AppUser.id)を軸に
 * 勤怠側から集計する、といった拡張がテーブル構造を変えずに行える。
 */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** タスクが属する部署(②)。会社単位のアクセス制御と二重に組み合わせて使う(㉑) */
    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    /** 担当ユーザー(⑧必須)。AppUser.id を参照する */
    @Column(name = "assigned_user_id", nullable = false)
    private Long assignedUserId;

    /** 登録者(管理者が割り当てた場合は管理者のAppUser.id、本人登録の場合は本人のAppUser.id)(⑦⑨) */
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    /** 仕事内容(⑥⑪) */
    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private TaskStatus status = TaskStatus.UNRESOLVED;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
