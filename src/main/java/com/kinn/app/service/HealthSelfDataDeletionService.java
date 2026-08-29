package com.kinn.app.service;

import com.kinn.app.repository.HealthAlertRepository;
import com.kinn.app.repository.HealthCheckRepository;
import com.kinn.app.repository.HealthProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康管理(拡張機能)の自己データ削除を扱うサービス。
 *
 * この機能は労働安全衛生法上のストレスチェック制度の代替ではなく、従業員本人による
 * 健康状態の自己記録・セルフケア支援を目的としたものであり、入力は完全に任意である
 * (docs/health-audit-legal-checklist.md 参照)。そのため、本人がいつでも自分の入力を
 * 取り消せる(削除できる)技術的な手段を用意しておく必要があり、このServiceがそれを担う。
 *
 * 削除対象: 健康プロフィール(health_profile)・体調チェックの全履歴(health_check)・
 * それらから自動生成された健康アラート(health_alert)。
 * 削除対象外(このServiceのスコープ外): 勤怠・タスク・スケジュール・食事記録等の他機能、
 * および従来の月次健康記録(health_record。1日単位の削除は既存のHealthController参照)。
 */
@Service
public class HealthSelfDataDeletionService {

    private final HealthProfileRepository profileRepository;
    private final HealthCheckRepository checkRepository;
    private final HealthAlertRepository alertRepository;

    public HealthSelfDataDeletionService(HealthProfileRepository profileRepository,
                                          HealthCheckRepository checkRepository,
                                          HealthAlertRepository alertRepository) {
        this.profileRepository = profileRepository;
        this.checkRepository = checkRepository;
        this.alertRepository = alertRepository;
    }

    /**
     * 指定した本人(employeeId)の健康プロフィール・体調チェック履歴・健康アラートを
     * すべて削除する。呼び出し元(Controller)は必ず、ログイン中の本人自身のemployeeIdだけを
     * 渡すこと(他人のデータを削除する用途では使わない)。
     *
     * health_alertはhealth_checkの記録から自動的に導出されるものなので、先に削除する
     * (削除順自体はFK制約が無いため必須ではないが、依存関係の順序として自然な並びにしている)。
     */
    @Transactional
    public void deleteAllForEmployee(String employeeId) {
        alertRepository.deleteByEmployeeId(employeeId);
        checkRepository.deleteByEmployeeId(employeeId);
        profileRepository.deleteByEmployeeId(employeeId);
    }
}
