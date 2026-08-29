package com.kinn.app.service;

import com.kinn.app.dto.AuthUserDto;
import com.kinn.app.dto.CompanyLookupDto;
import com.kinn.app.dto.RegisterRequestDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Company;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.CompanyRepository;
import com.kinn.app.repository.DepartmentRepository;
import com.kinn.app.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

/**
 * 新規登録・ログイン名解決を担当するサービス。
 * パスワードの検証そのものはAuthenticationManager(DaoAuthenticationProvider)に委ねる
 * (独自の簡易認証にしない)。
 */
@Service
public class AuthService {

    private final CompanyRepository companyRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyCodeGenerator companyCodeGenerator;

    public AuthService(CompanyRepository companyRepository,
                        AppUserRepository appUserRepository,
                        DepartmentService departmentService,
                        DepartmentRepository departmentRepository,
                        PasswordEncoder passwordEncoder,
                        CompanyCodeGenerator companyCodeGenerator) {
        this.companyRepository = companyRepository;
        this.appUserRepository = appUserRepository;
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.companyCodeGenerator = companyCodeGenerator;
    }

    /**
     * 新規登録。mode="CREATE"(新しく会社を登録する)/"JOIN"(既存の会社に参加する)で
     * 挙動を分ける(RegisterRequestDtoのjavadoc参照)。
     *
     * 会社名の文字列一致でテナントを決定する旧方式は廃止した。偶然同じ社名の別会社が
     * 誤って同じテナントに混在する事故を防ぐため、CREATEは常に新しいCompanyを作成し、
     * JOINは必ずcompany_code(一意)で対象会社を特定する。
     *
     * 部署は Company → Department → AppUser の階層で管理する。CREATE時のみ、
     * 入力された部署名で最初の部署を自由に作成できる(その会社にとって最初のユーザーのため)。
     * JOIN時は参加先の会社に登録済みの部署名しか指定できない(存在しない部署名では登録できない)。
     *
     * 会社がこの登録で新規作成された場合(CREATE)、そのユーザーは自動的にADMIN権限になる
     * (でなければ、その会社を管理できる人間が誰もいない状態になってしまうため)。
     */
    @Transactional
    public AuthUserDto register(RegisterRequestDto dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードが一致しません");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "メールアドレスを入力してください");
        }

        boolean isCreate = "CREATE".equalsIgnoreCase(dto.getMode());
        boolean isJoin = "JOIN".equalsIgnoreCase(dto.getMode());
        if (!isCreate && !isJoin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登録方法の指定が正しくありません");
        }

        Company company;
        if (isCreate) {
            if (dto.getCompanyName() == null || dto.getCompanyName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会社名を入力してください");
            }
            String companyName = dto.getCompanyName().trim();
            // company.nameにユニーク制約は無いため、技術的には同名の会社を何個でも作成できる。
            // しかし同名の会社が複数になると、ログイン画面で会社名を入力しても一意に特定できず
            // ログインできなくなる(resolveCompanyByNameOrCodeのjavadoc参照。実際に発生し、
            // セキュリティレビュー4-2で指摘された)。既存の別会社が意図的に同じ社名を名乗る
            // ケースまでは禁止したくないため、ハード ブロックはせず、確認なしでは先に進めない
            // ようにする(register.js側がこの422を検知し、確認ダイアログを出してから
            // confirmDuplicateName=trueで再送信する)。
            if (!dto.isConfirmDuplicateName() && companyRepository.existsByName(companyName)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "「" + companyName + "」という名前の会社は既に登録されています。"
                                + "同じ会社であれば、新規登録ではなく「既存の会社に参加する」から会社コードで参加してください。"
                                + "別の会社として、あえて同じ名前のまま新規登録する場合は、もう一度「登録」を押してください。");
            }
            company = companyRepository.save(Company.builder()
                    .name(companyName)
                    .companyCode(companyCodeGenerator.generateUnique())
                    .build());
        } else {
            if (dto.getCompanyCode() == null || dto.getCompanyCode().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会社コードを入力してください");
            }
            company = companyRepository.findByCompanyCode(normalizeCompanyCode(dto.getCompanyCode()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "会社コードが正しくありません"));
        }

        if (appUserRepository.existsByCompanyIdAndLoginId(company.getId(), dto.getLoginId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このユーザーIDは既に登録されています。");
        }

        // CREATEのみ自由入力(その会社にとって最初の部署)を許可する。JOINは必ず既存の部署から選ぶ。
        Department department = departmentService.resolveForRegistration(
                company.getId(), dto.getDepartmentName(), isCreate);

        AppUser user = AppUser.builder()
                .companyId(company.getId())
                .loginId(dto.getLoginId().trim())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getFullName().trim())
                .email(dto.getEmail().trim())
                .departmentId(department.getId())
                .role(isCreate ? UserRole.ADMIN : UserRole.USER)
                .build();
        appUserRepository.save(user);

        return toDto(user, company, department.getName());
    }

    /**
     * 新規登録画面(既存の会社に参加する場合)が、入力された会社コードから会社名・部署の
     * 選択肢を組み立てるために使う(未ログインでも呼べる公開API)。会社コードが一致しない
     * 場合は404にする(存在しないコードであることを画面側がそのまま案内してよい。
     * ログインのアカウント存在有無とは異なり、company_codeは元々同僚間で共有される
     * 前提の値であり、詳細を隠す必要性がログインの資格情報ほど高くないため)。
     */
    @Transactional(readOnly = true)
    public CompanyLookupDto lookupCompanyByCode(String companyCode) {
        Company company = companyRepository.findByCompanyCode(normalizeCompanyCode(companyCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会社コードが正しくありません"));
        return CompanyLookupDto.builder()
                .companyName(company.getName())
                .departmentNames(departmentService.getDepartmentNames(company.getId()))
                .build();
    }

    /** 会社コードの表記ゆれ(前後空白・大小文字)を吸収する */
    private String normalizeCompanyCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * ログイン成功が確定した後に呼び出す専用メソッド(トップページのログイン時間表示用)。
     * 既存のtoDto()系のロジックには手を入れず、呼び出し側(AuthController)が
     * 認証成功(パスワード照合・アカウントロック/無効化チェック・セッション認証戦略まで)を
     * 確認した後にこれを呼ぶことで、失敗したログイン試行ではlastLoginAtが更新されないようにする。
     */
    @Transactional
    public void recordLogin(AppUser user) {
        user.setLastLoginAt(java.time.LocalDateTime.now());
        appUserRepository.save(user);
    }

    /**
     * ログイン画面の「会社名」欄を実際のcompanyIdへ解決し、
     * AuthenticationManagerへ渡す "companyId|loginId" 形式のusernameを組み立てる。
     * 会社が(会社名でも会社コードでも)一件も見つからない場合は、ここでは詳細を明かさず
     * BadCredentialsExceptionを投げ、呼び出し側(AuthController)でパスワード誤りと同じ
     * 汎用メッセージにする(アカウントの存在を特定されにくくするため)。
     * 会社名が複数の会社に一致してしまい一意に特定できない場合は、resolveCompanyByNameOrCode
     * 側がResponseStatusExceptionを投げる(そちらのjavadoc参照。この場合だけは
     * 「会社コードでログインしてください」と具体的に案内する)。
     */
    @Transactional(readOnly = true)
    public String resolveUsername(String companyNameOrCode, String loginId) {
        Company company = resolveCompanyByNameOrCode(companyNameOrCode)
                .orElseThrow(() -> new BadCredentialsException("invalid company"));
        return company.getId() + "|" + loginId.trim();
    }

    /**
     * 「会社名または会社コード」の入力欄から会社を解決する共通ロジック。ログイン
     * (resolveUsername)専用。
     *
     * company.nameのユニーク制約を撤廃した(会社識別方法の見直し)ことにより、同名の会社が
     * 複数存在し得る。それに対応するため、会社名だけでなく会社コード
     * (新規登録時にADMINへ発行される一意な値)でも解決できるようにしている。
     * 1. まずcompany_codeとして完全一致するか調べる(一致すれば一意に確定するため最優先)。
     * 2. 一致しなければ会社名として調べる。同名の会社が複数あってfindByNameが一意な結果を
     *    返せない場合(IncorrectResultSizeDataAccessException)は、以前はOptional.empty()として
     *    扱い「会社が見つからない」のと同じ汎用エラー(会社名、ユーザーID、またはパスワードが
     *    正しくありません)にしていたが、これだとユーザーID・パスワードが正しくても
     *    原因不明のままログインできない状態になってしまっていた(セキュリティレビュー4-2で指摘)。
     *    そのため、この「複数の会社と一致してしまい一意に特定できない」ケースだけは
     *    ResponseStatusExceptionで明確に案内するよう変更した。会社の存在有無やアカウントの
     *    有効性そのものは明かしていない(「この名前は複数の会社が使っている」という事実のみを伝える)。
     */
    @Transactional(readOnly = true)
    public Optional<Company> resolveCompanyByNameOrCode(String companyNameOrCode) {
        String trimmed = companyNameOrCode.trim();

        Optional<Company> byCode = companyRepository.findByCompanyCode(trimmed.toUpperCase(Locale.ROOT));
        if (byCode.isPresent()) {
            return byCode;
        }
        try {
            return companyRepository.findByName(trimmed);
        } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "入力された会社名に一致する会社が複数見つかったため、会社名では特定できません。"
                            + "会社コード(管理者トップ画面で確認できます)でログインしてください。");
        }
    }

    @Transactional(readOnly = true)
    public AuthUserDto toDto(AppUserPrincipal principal) {
        AppUser user = principal.getAppUser();
        Company company = companyRepository.findById(user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "会社情報が見つかりません"));
        String departmentName = user.getDepartmentId() == null ? null
                : departmentRepository.findById(user.getDepartmentId()).map(Department::getName).orElse(null);
        return toDto(user, company, departmentName);
    }

    private AuthUserDto toDto(AppUser user, Company company, String departmentName) {
        return AuthUserDto.builder()
                .userId(user.getId())
                .loginId(user.getLoginId())
                .fullName(user.getFullName())
                .companyName(company.getName())
                // 会社コードは同僚の入社登録に使う情報のため管理者にのみ返す(AuthUserDtoのjavadoc参照)
                .companyCode(user.getRole() == UserRole.ADMIN ? company.getCompanyCode() : null)
                .departmentName(departmentName)
                .role(user.getRole().name())
                .lastLoginAt(user.getLastLoginAt())
                .mustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword())
                .build();
    }
}
