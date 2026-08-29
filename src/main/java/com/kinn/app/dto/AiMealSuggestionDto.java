package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** AIによる1日分(朝食・昼食・夕食)の献立提案。画面表示・履歴表示・DB保存で共通利用する */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMealSuggestionDto {
    private Long id;
    /** 提案対象日(この献立を食べる日) */
    private LocalDate suggestionDate;
    /** 分析の元にした前日の日付 */
    private LocalDate basedOnDate;
    /** 同じ日への提案の通し番号(「別の献立を提案」のたびに+1) */
    private int attemptNo;
    /** "AI" または "RULE_BASED"(HealthScoreDtoのlevelと同様、表示用の文字列として持つ) */
    private String source;
    /** 実AIが利用可能な状態で生成されたかどうか(falseの場合はルールベースにフォールバック済み) */
    private boolean aiAvailable;
    /** 提案全体の前提・総評 */
    private String summaryNote;
    /** ユーザーが「⭐ この献立を保存」を押したかどうか */
    private boolean saved;
    private LocalDateTime generatedAt;

    private AiMealSuggestionItemDto breakfast;
    private AiMealSuggestionItemDto lunch;
    private AiMealSuggestionItemDto dinner;
}
