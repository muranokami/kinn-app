package com.kinn.app.audit;

import com.kinn.app.entity.HealthAuditAction;
import com.kinn.app.entity.HealthAuditResource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * このアノテーションを付けたControllerメソッドの呼び出しを {@link HealthAuditAspect} が
 * 横断的に監査ログ(health_audit_log)へ記録する。
 *
 * 健康管理機能は対象操作が閲覧を含め多数にわたるため、AttendanceAuditのようにController側で
 * 都度ログ保存処理を呼び出すのではなく、このアノテーションを1行付けるだけで記録されるようにする
 * (ロジック本体には一切手を入れない)。
 *
 * 使い方の例:
 * <pre>
 *   {@literal @}Audited(resource = HealthAuditResource.PROFILE, action = HealthAuditAction.VIEW)
 *   {@literal @}GetMapping
 *   public HealthProfileDto getProfile(Authentication authentication) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 対象リソース種別 */
    HealthAuditResource resource();

    /** 操作種別 */
    HealthAuditAction action();

    /** 操作対象ユーザーの決め方。デフォルトは「本人」 */
    AuditTarget target() default AuditTarget.SELF;

    /**
     * 対象データの識別情報(日付・レコードIDなど)を抽出するSpEL式(省略可)。
     * メソッドの引数名をそのまま "#引数名" で参照できる(例: "#date", "#dto.checkDate")。
     * 健康情報の値そのもの(体重・血圧・メモ等)を返す式を書かないこと。
     */
    String ref() default "";
}
