package com.kinn.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 退職者アカウント等を無効化/再有効化するためのリクエスト。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeEnabledUpdateRequestDto {
    @NotNull(message = "有効/無効を指定してください")
    private Boolean enabled;
}
