package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 月次のスケジュール集計。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSummaryDto {
    private int year;
    private int month;

    private int totalEvents;
    private int workCount;
    private int meetingCount;
    private int privateCount;
    private int healthCount;
    private int otherCount;
}
