package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 会社(テナント)。ログイン画面の「会社名」に対応する。
 *
 * このアプリの既存Entity(MealRecord, AttendanceRecord など)はすべて
 * employee_id という単一のvarchar文字列でユーザーを識別する設計になっている。
 * 会社ごとのデータ分離を、既存テーブルすべてに company_id 列を追加するような
 * 大規模なマイグレーションなしで実現するため、ログイン後に払い出す実効ユーザーID
 * (AppUser#effectiveEmployeeId, 形式 "{companyId}|{loginId}") に会社情報を
 * 織り込んでいる。これにより既存テーブルは一切変更せずに会社単位の分離ができる。
 *
 * nameのユニーク制約は撤廃した(2026-08-29)。偶然同じ社名の別会社が、新規登録時に
 * 会社名の文字列一致だけで誤って同じテナントに混在してしまう事故を防ぐため、
 * テナントの実際の識別・参加はcompanyCode(システムが発行する一意なコード)で行う
 * 設計に変更した(AuthService#register参照)。nameはあくまで表示・検索の補助であり、
 * 複数の会社が同じnameを持つことを許容する。
 *
 * ログイン(AuthService#resolveUsername)は既存の「会社名」入力方式を変更していないため、
 * 実際に同名の会社が複数存在する状態になった場合、会社名だけでは一意に定まらずログインが
 * エラーになり得る(意図的な設計: 誤って別テナントへ紛れ込むより、エラーで気付ける方が安全)。
 */
@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * システムが自動発行する一意な会社コード(例: 英数字8桁)。テナントへの参加は必ずこの値で行う
     * (CompanyCodeGenerator参照)。JPA上はnullableのままにしている(既存行に対する
     * CompanyCodeMigrationRunnerでの後埋めが、Hibernateのddl-auto=updateによる
     * NOT NULL制約の即時付与で失敗しないようにするため。DepartmentMigrationRunnerと同じ方針)。
     * アプリケーションのロジック上は、新規作成される会社には必ず発行してから保存する。
     */
    @Column(name = "company_code", unique = true, length = 12)
    private String companyCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
