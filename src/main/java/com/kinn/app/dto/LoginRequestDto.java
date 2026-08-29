package com.kinn.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDto {

    @NotBlank(message = "会社名を入力してください")
    private String companyName;

    @NotBlank(message = "ユーザーIDを入力してください")
    private String loginId;

    @NotBlank(message = "パスワードを入力してください")
    private String password;
}
