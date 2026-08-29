package com.kinn.app.service;

import com.kinn.app.dto.AiRawRecipeDto;
import com.kinn.app.dto.AiRawSuggestionDto;

import java.util.Optional;

/**
 * AI献立提案・AIレシピ生成(Phase 5)のためのAI API呼び出しを担当するクライアントの契約。
 * 実装は{@link AnthropicMealClient}(Anthropic Messages APIを実際に呼び出す)。
 *
 * インターフェースとして切り出しているのは主にテスト容易性のため(このプロジェクトの実行環境
 * ではMockitoのインラインモックがJava 24のクラスファイルを扱えず、具象クラスを直接モックできない
 * 制約があるため、他のService層と同様「振る舞いを差し替えたい依存先はインターフェースにする」
 * 方針に合わせている。AttendanceServiceGetPeriodTest等、既存の実装本体を使うテストとは異なり、
 * AiMealClientは外部HTTP通信そのものが振る舞いのため、実装を丸ごと差し替える必要がある)。
 *
 * ・APIキー未設定/通信エラー/レスポンス不正のいずれの場合も例外を投げず Optional.empty() を返す。
 *   呼び出し元(MealRecommendationService/RecipeService)はこれを見て、献立提案はルールベースへ
 *   フォールバックし、レシピ生成はユーザーにエラーメッセージを表示する(㉒)。
 *   いずれにせよAI APIが利用できない状況でもアプリ全体はエラーにならない。
 */
public interface AiMealClient {

    /** APIキーが設定されているかどうか(未設定ならAI呼び出し自体を試みない) */
    boolean isConfigured();

    /**
     * プロンプトをAIに送信し、構造化された献立提案を取得する。
     * 未設定・通信失敗・JSON解析失敗など、いかなる理由でも失敗時は Optional.empty()。
     */
    Optional<AiRawSuggestionDto> generate(String prompt);

    /**
     * プロンプトをAIに送信し、構造化されたレシピ(材料・調理方法・調理手順等)を取得する(Phase 5)。
     * 未設定・通信失敗・JSON解析失敗など、いかなる理由でも失敗時は Optional.empty()
     * (呼び出し元のRecipeServiceがこれを見てユーザーにエラーメッセージを表示する。㉒)。
     */
    Optional<AiRawRecipeDto> generateRecipe(String prompt);
}
