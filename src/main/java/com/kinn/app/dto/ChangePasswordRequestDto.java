package com.kinn.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // RegisterRequestDto.passwordと同じ強度基準にする(登録時とパスワード変更時で
    // 強度基準が食い違わないようにするため)。記号の使用は許可するが必須にはしない。
    @NotBlank(message = "新しいパスワードを入力してください")
    @Size(min = 8, message = "新しいパスワードは8文字以上で入力してください")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
            message = "新しいパスワードは英字の大文字・小文字・数字をすべて含めてください")
    private String newPassword;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;
}
