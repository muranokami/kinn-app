package com.kinn.app.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /**
     * CREATE時のみ使用。同名の会社が既に存在する場合、AuthService#registerは422で
     * 一度確認を求める(会社名が重複するとログイン時に会社名だけでは特定できなくなるため。
     * resolveCompanyByNameOrCodeのjavadoc参照)。register.jsが確認ダイアログを出した上で
     * ユーザーが「それでも登録する」を選んだ場合のみtrueにして再送信する。
     */
    @Builder.Default
    private boolean confirmDuplicateName = false;

    /** JOIN時のみ必須: 参加先の会社コード */
    private String companyCode;

    /**
     * 部署名。CREATE時は自由入力(最初の部署として新規作成される)。
     * JOIN時は参加先の会社に登録済みの部署から選ぶ必要がある(存在しない部署名は登録できない)。
     */
    @NotBlank(message = "部署を選択してください")
    private String departmentName;

    // 半角英数字のみ・4〜32文字に制限する(以前は文字種の制約が無く、日本語・記号なども
    // 入力できてしまっていた。セキュリティレビューで指摘・修正)。既存ユーザーのloginIdは
    // この変更の影響を受けない(ログイン時のバリデーションではなく新規登録時のみ適用されるため)。
    @NotBlank(message = "ユーザーIDを入力してください")
    @Size(min = 4, max = 32, message = "ユーザーIDは4文字以上32文字以内で入力してください")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "ユーザーIDは半角英数字のみで入力してください")
    private String loginId;

    // ChangePasswordRequestDtoの新パスワードと同じ最低文字数(8文字)・複雑さ要件にする
    // (登録時とパスワード変更時で強度基準が食い違わないようにするため)。
    // 以前はここに@Sizeが無く、登録時(特に会社を新規作成する最初の管理者アカウント)だけ
    // 1文字のパスワードでも作成できてしまっていた(セキュリティレビューで指摘・修正)。
    // 記号の使用は許可するが必須にはしない(今回の要件に無いため)。
    @NotBlank(message = "パスワードを入力してください")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$",
            message = "パスワードは英字の大文字・小文字・数字をすべて含めてください")
    private String password;

    @NotBlank(message = "確認用パスワードを入力してください")
    private String confirmPassword;

    @NotBlank(message = "氏名を入力してください")
    private String fullName;

    /**
     * 管理者による本人確認・連絡先として使う(セルフサービス型のメールパスワードリセットは
     * 個人情報漏洩防止の観点から実装していない。SecurityConfigのjavadoc参照)。新規登録では必須
     */
    @NotBlank(message = "メールアドレスを入力してください")
    @Email(message = "メールアドレスの形式が正しくありません")
    private String email;
}
