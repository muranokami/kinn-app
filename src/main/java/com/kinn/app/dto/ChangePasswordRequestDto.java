package com.kinn.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 本人によるパスワード変更リクエスト。管理者リセット直後の強制変更・任意の変更のどちらでも使う。
 *
 * 現在のパスワードの入力は求めない仕様(PasswordService#changePassword参照。忘れてしまった
 * 現在のパスワードが分からず変更もできない、という手詰まりを避けるための判断)。
 *
 * loginIdはセッションから自明な値だが、あえて画面上に入力させて送らせることで、
 * 「今どのアカウントのパスワードを変更しようとしているか」を操作者自身の目で確認できるようにする
 * (AuthController#changePasswordでログイン中の本人のloginIdと一致することを検証する)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequestDto {

    @NotBlank(message = "ユーザーIDを入力してください")
    private String loginId;

    @NotBlank(message = "新しいパスワードを入力してください")
    @Size(min = 8, message = "新しいパスワードは8文字以上で入力してください")
    private String newPassword;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;
}
