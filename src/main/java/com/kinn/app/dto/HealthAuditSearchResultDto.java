package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 監査ログ検索結果+保持期間(設定値。ハードコードしない)をまとめて返すDTO。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthAuditSearchResultDto {
    private List<HealthAuditLogDto> items;
    /** application.properties (app.audit.health.retention-days) の設定値 */
    private int retentionDays;
}
