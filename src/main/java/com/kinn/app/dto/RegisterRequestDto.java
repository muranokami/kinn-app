package com.kinn.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 新規登録リクエスト。modeによって「新しく会社を登録する(CREATE)」か
 * 「既存の会社に参加する(JOIN)」かを切り替える(register.html参照)。
 *
 * ・CREATE: companyNameが必須、companyCodeは無視する。会社は必ず新規作成され、
 *   自動発行されたcompany_codeがレスポンス(AuthUserDto)で一度返る。
 * ・JOIN: companyCodeが必須、companyNameは無視する。company_codeが一致する会社にのみ
 *   参加できる(会社名の文字列一致でテナントを決定する旧方式は廃止した)。
 *
 * mode/companyName/companyCodeの組み合わせ整合性はBean Validationでは表現しにくいため、
 * AuthService#register内で明示的にチェックする。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDto {

    /** "CREATE"(新しく会社を登録する) または "JOIN"(既存の会社に参加する) */
    @NotBlank(message = "登録方法を選択してください")
    private String mode;

    /** CREATE時のみ必須: 新しく作る会社の名前 */
    private String companyName;

    /** JOIN時のみ必須: 参加先の会社コード */
    private String companyCode;

    /**
     * 部署名。CREATE時は自由入力(最初の部署として新規作成される)。
     * JOIN時は参加先の会社に登録済みの部署から選ぶ必要がある(存在しない部署名は登録できない)。
     */
    @NotBlank(message = "部署を選択してください")
    private String departmentName;

    @NotBlank(message = "ユーザーIDを入力してください")
    private String loginId;

    @NotBlank(message = "パスワードを入力してください")
    private String password;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;

    @NotBlank(message = "氏名を入力してください")
    private String fullName;

    /** セルフサービス型パスワードリセット(ForgotPasswordService)に使う。新規登録では必須 */
    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;
}
