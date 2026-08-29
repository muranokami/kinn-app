package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AIによる「翌日(=提案対象日)の朝食・昼食・夕食」提案の1回分(ヘッダー行)。
 * 実際の献立3食分は {@link AiMealSuggestionItem} に紐づく(suggestionId で関連付け。
 * このアプリの既存Entityに倣い、@ManyToOne等の関連は張らずFKカラムのみで持つ)。
 *
 * 「別の献立を提案」を押すたびに attemptNo をインクリメントして新しい行を追加する
 * (過去の提案も履歴として残るため、上書きはしない)。
 */
@Entity
@Table(name = "ai_meal_suggestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMealSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 複数人対応を見据えた社員ID。単一ユーザーで使う場合は "default" 固定でよい */
    @Column(name = "employee_id", nullable = false, length = 64)
    @Builder.Default
    private String employeeId = "default";

    /** 提案対象日(=この献立を食べる日。通常は「今日」) */
    @Column(name = "suggestion_date", nullable = false)
    private LocalDate suggestionDate;

    /** 分析の元にした前日の日付(前日データが無い場合もsuggestionDateの前日を記録する) */
    @Column(name = "based_on_date")
    private LocalDate basedOnDate;

    /** 同じ日に対する提案の通し番号(1から開始。「別の献立を提案」のたびに+1) */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private AiSuggestionSource source;

    /** 提案全体の総評・前提(例:「前日の記録が少ないため一般的なバランスで提案」) */
    @Column(name = "summary_note", length = 500)
    private String summaryNote;

    /** ユーザーが「⭐ この献立を保存」を押したかどうか */
    @Column(name = "is_saved", nullable = false)
    @Builder.Default
    private boolean saved = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
