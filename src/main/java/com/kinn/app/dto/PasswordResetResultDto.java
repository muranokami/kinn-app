package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理者による強制パスワードリセットの結果。temporaryPasswordはこのレスポンス限りでのみ渡され、
 * サーバー側には平文で保持しない(DBにはBCryptハッシュのみ保存)。管理者の画面で一度だけ表示し、
 * 以降は二度と取得できない。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetResultDto {
    private Long userId;
    private String loginId;
    private String fullName;
    /** 生成した一時パスワード(平文)。この応答限りでのみ有効 */
    private String temporaryPassword;
}
