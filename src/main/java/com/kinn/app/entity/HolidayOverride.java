package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 祝日カレンダーの手動オーバーライド(追加/取消)。
 *
 * {@link com.kinn.app.service.JapaneseHolidayCalculator} はアルゴリズムで祝日を算出するが、
 * 法改正による一時的な移動(例: 2020年東京オリンピックの特例で海の日/山の日/スポーツの日が
 * 移動した等)には追随できない。このテーブルに行があれば、その日付についてはアルゴリズムの
 * 判定より必ず優先される(isHoliday=true なら祝日として追加、false なら祝日から除外する)。
 *
 * 「祝日判定の仕組みはハードコードしすぎず、将来的に祝日カレンダーを更新できる構造にする」
 * という要件を、コード変更なしで満たすための拡張ポイント。
 */
@Entity
@Table(name = "holiday_override")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate date;

    /** 祝日名(isHoliday=falseの場合は「取消理由」的な扱いでも可、null許容) */
    @Column(name = "name", length = 100)
    private String name;

    /** true=この日を祝日として追加する / false=アルゴリズム判定を打ち消して祝日から除外する */
    @Column(name = "is_holiday", nullable = false)
    @Builder.Default
    private boolean holiday = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
