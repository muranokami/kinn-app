package com.kinn.app.service;

import com.kinn.app.repository.CompanyRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 会社コード(company_code)の生成。新規会社の作成時(AuthService#register)と、
 * 既存データの後埋め(CompanyCodeMigrationRunner)の両方から使う共通ロジック。
 *
 * 文字集合は一時パスワード(PasswordService)と同じ考え方で、1/l/I、0/Oのような
 * 見間違えやすい文字を除いた英数字にする(電話・チャット等での伝達を想定し視認性を優先)。
 * 生成のたびにDBを引いて一意性を確認し、衝突していれば再生成する
 * (8桁・約32種類の文字集合なので衝突確率は極めて低いが、念のため確実性を優先する)。
 */
@Component
public class CompanyCodeGenerator {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 20;

    private final CompanyRepository companyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public CompanyCodeGenerator(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public String generateUnique() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = generate();
            if (!companyRepository.existsByCompanyCode(candidate)) {
                return candidate;
            }
        }
        // 32^8通りに対してこの回数連続で衝突するのは実質あり得ないが、
        // 万一発生した場合は原因調査できるよう明示的に失敗させる(サイレントな重複を防ぐ)。
        throw new IllegalStateException("会社コードの生成に失敗しました(候補が枯渇しました)");
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
