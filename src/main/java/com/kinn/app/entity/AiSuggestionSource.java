package com.kinn.app.entity;

/**
 * AI献立提案がどのロジックで生成されたかを表す。
 * AI APIキー未設定時やAPI呼び出し失敗時は自動的に RULE_BASED にフォールバックする
 * (画面がエラーにならないようにするための切り替え。MealRecommendationService参照)。
 */
public enum AiSuggestionSource {
    AI("AI"),
    RULE_BASED("ルールベース");

    private final String label;

    AiSuggestionSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
