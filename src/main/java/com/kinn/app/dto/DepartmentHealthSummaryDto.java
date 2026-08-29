package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 部署単位の健康集計。部署未登録の場合は department が "未設定" になる。
 * 現状は社員ごとに手動登録した department 文字列を使った簡易集計であり、
 * 部署マスタが整備された際にはそちらに置き換えられる想定。
 *
 * insufficientData=true の場合、employeeCount(と部署名)以外の集計値は意図的にnullのまま
 * (AdminHealthServiceのjavadoc参照)。人数が少ない部署で平均値・アラート件数をそのまま
 * 出すと、実質的に個人の健康状態を特定できてしまう(例: 2人の部署で「平均ストレス度が高い」と
 * 出れば、どちらか/両方の状態がほぼ分かってしまう)ため、しきい値未満の部署は数値を返さない。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentHealthSummaryDto {
    private String department;
    private int employeeCount;
    /** true の場合、対象人数が少なく個人特定リスクがあるため集計値を意図的に出していない */
    private boolean insufficientData;
    /** 画面側が「◯名未満のため」の案内文を組み立てるための、判定に使ったしきい値 */
    private int minEmployeeCountThreshold;
    private Double avgHealthScore;
    private Double avgSleepHours;
    private Double avgFatigueLevel;
    private Double avgStressLevel;
    private Double avgOvertimeHours;
    private Long alertCount;
}
