package com.kinn.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 祝日カレンダーの手動オーバーライド(追加/取消)の登録・表示用DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayOverrideDto {
    private Long id;

    @NotNull(message = "日付は必須です")
    private LocalDate date;

    private String name;

    @NotNull(message = "祝日として追加/除外するかの指定は必須です")
    private Boolean holiday;
}
