package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** カレンダー表示・CSV出力用の祝日1件(日付+祝日名) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayDto {
    private LocalDate date;
    private String name;
}
