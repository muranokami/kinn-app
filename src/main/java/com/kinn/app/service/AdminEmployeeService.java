package com.kinn.app.service;

import com.kinn.app.dto.AdminCreateEmployeeRequestDto;
import com.kinn.app.dto.AttendanceRecordDto;
import com.kinn.app.dto.EmployeeDetailDto;
import com.kinn.app.dto.EmployeeSummaryDto;
import com.kinn.app.dto.EmployeeUpdateRequestDto;
import com.kinn.app.dto.PasswordResetResultDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Company;
import com.kinn.app.entity.DayType;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.CompanyRepository;
import com.kinn.app.repository.DepartmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理者向け従業員管理(一覧・検索・詳細・新規登録・権限変更・部署変更)。
 *
 * 会社単位のアクセス制御: すべてのメソッドが companyId を引数に取り、
 * Repository層のクエリ自体に companyId 条件を含めることで、他社の従業員へは
 * 構造的に到達できないようにしている(IDを書き換えるだけでは他社データへ届かない)。
 * 部署についても同様に、DepartmentService#requireOwned で「その部署が本当に
 * 管理者と同じ会社に属しているか」を必ず確認してから紐付ける。
 *
 * 健康・食事データは一切参照しない(勤怠管理に必要な情報のみを扱う)。
 */
@Service
public class AdminEmployeeService {

    /**
     * 会社ごとに常に維持すべき「ログイン可能な管理者」の最小人数。
     * 管理者が1人しかいない状態だと、その本人がパスワードを忘れた場合に誰もリセットできず
     * (メール送信基盤が無くセルフリセットもできないため)復旧不能になる。これを避けるため、
     * 削除・権限降格・無効化のいずれの操作でも、この人数を下回る結果になる操作は拒否する
     * (新規登録直後などで既に1人しかいない会社を強制的に直すものではなく、これ以上減らさせない
     * ための安全策)。
     */
    private static final int MIN_ACTIVE_ADMINS = 2;

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;
    private final AttendanceService attendanceService;
    private final PasswordService passwordService;

    public AdminEmployeeService(AppUserRepository appUserRepository,
                                 CompanyRepository companyRepository,
                                 DepartmentRepository departmentRepository,
                                 DepartmentService departmentService,
                                 PasswordEncoder passwordEncoder,
                                 AttendanceService attendanceService,
                                 PasswordService passwordService) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.departmentService = departmentService;
        this.passwordEncoder = passwordEncoder;
        this.attendanceService = attendanceService;
        this.passwordService = passwordService;
    }

    /** 従業員一覧(検索条件は任意。氏名/ユーザーIDの部分一致 + 部署IDの完全一致で絞り込む) */
    @Transactional(readOnly = true)
    public List<EmployeeSummaryDto> getEmployees(Long companyId, String keyword, Long departmentId) {
        String kw = (keyword == null) ? "" : keyword.trim().toLowerCase(Locale.JAPAN);
        LocalDate today = LocalDate.now();

        List<AppUser> users = (departmentId != null)
                ? appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(companyId, departmentId)
                : appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId);

        Map<Long, String> departmentNames = departmentNameMap(companyId);

        return users.stream()
                .filter(u -> kw.isEmpty()
                        || u.getFullName().toLowerCase(Locale.JAPAN).contains(kw)
                        || u.getLoginId().toLowerCase(Locale.JAPAN).contains(kw))
                .map(u -> toSummaryDto(u, today, departmentNames))
                .toList();
    }

    /** 従業員詳細。companyIdが一致しない場合は404(他社の従業員IDを指定しても情報は一切返さない) */
    @Transactional(readOnly = true)
    public EmployeeDetailDto getEmployeeDetail(Long companyId, Long userId) {
        AppUser user = findOwned(companyId, userId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "会社情報が見つかりません"));
        return EmployeeDetailDto.builder()
                .userId(user.getId())
                .loginId(user.getLoginId())
                .fullName(user.getFullName())
                .companyName(company.getName())
                .departmentId(user.getDepartmentId())
                .departmentName(departmentService.getDepartmentNameOrNull(user.getDepartmentId()))
                .position(user.getPosition())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .enabled(user.getEnabled() == null || user.getEnabled())
                .build();
    }

    /** 管理者による従業員の新規登録。所属会社は必ず管理者自身の会社になる(選択不可) */
    @Transactional
    public EmployeeDetailDto createEmployee(Long companyId, AdminCreateEmployeeRequestDto dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "パスワードが一致しません");
        }
        if (appUserRepository.existsByCompanyIdAndLoginId(companyId, dto.getLoginId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このユーザーIDは既に登録されています。");
        }

        // 部署を指定した場合は、必ず自社の部署であることを確認する(他社の部署IDを紐付けさせない)
        Long departmentId = null;
        if (dto.getDepartmentId() != null) {
            departmentId = departmentService.requireOwned(companyId, dto.getDepartmentId()).getId();
        }

        UserRole role = UserRole.USER;
        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            try {
                role = UserRole.valueOf(dto.getRole().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "権限の指定が正しくありません");
            }
        }

        AppUser user = AppUser.builder()
                .companyId(companyId)
                .loginId(dto.getLoginId().trim())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getFullName().trim())
                .departmentId(departmentId)
                .position(blankToNull(dto.getPosition()))
                .email(blankToNull(dto.getEmail()))
                .role(role)
                .build();
        appUserRepository.save(user);

        return getEmployeeDetail(companyId, user.getId());
    }

    /**
     * 権限変更。管理者が自分自身の権限を変更することは禁止する(自己昇格・自己降格の事故防止。
     * 一般ユーザーがこのAPIへ到達すること自体はSecurityConfigの hasRole("ADMIN") で既に
     * 防がれているが、万一の実装ミスに備えた多重防御でもある)。
     * ADMINから他の権限へ降格させる操作は、それによってログイン可能な管理者が
     * MIN_ACTIVE_ADMINS人を下回る場合は拒否する(唯一の管理者が孤立する事故防止)。
     */
    @Transactional
    public EmployeeDetailDto updateRole(Long companyId, Long adminUserId, Long targetUserId, String newRole) {
        if (adminUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身の権限は変更できません");
        }
        AppUser user = findOwned(companyId, targetUserId);
        UserRole role;
        try {
            role = UserRole.valueOf(newRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "権限の指定が正しくありません");
        }
        if (user.getRole() == UserRole.ADMIN && role != UserRole.ADMIN && isEnabled(user)) {
            requireAdminSurplus(companyId, "管理者の人数が%d名を下回るため、この操作はできません。先に他の従業員を管理者にしてください。");
        }
        user.setRole(role);
        appUserRepository.save(user);
        return getEmployeeDetail(companyId, targetUserId);
    }

    /**
     * アカウントの有効/無効切り替え(退職者アカウントを即座にログイン不可にするためのもの)。
     * 権限変更・削除と同じ考え方で、管理者が自分自身を無効化することは禁止する
     * (誤操作で自分自身がログインできなくなる事故防止)。
     * 管理者を無効化する操作も、それによってログイン可能な管理者がMIN_ACTIVE_ADMINS人を
     * 下回る場合は拒否する(役職を変えずとも無効化だけでも同じ孤立事故が起こり得るため)。
     */
    @Transactional
    public EmployeeDetailDto updateEnabled(Long companyId, Long adminUserId, Long targetUserId, boolean enabled) {
        if (adminUserId.equals(targetUserId) && !enabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身を無効化することはできません");
        }
        AppUser user = findOwned(companyId, targetUserId);
        if (!enabled && user.getRole() == UserRole.ADMIN) {
            requireAdminSurplus(companyId, "管理者の人数が%d名を下回るため無効化できません。先に他の従業員を管理者にしてください。");
        }
        user.setEnabled(enabled);
        appUserRepository.save(user);
        return getEmployeeDetail(companyId, targetUserId);
    }

    /**
     * 所属部署の変更。departmentId=null は「未所属」に戻すことを表す。
     * 指定した部署が管理者自身の会社に属していることを必ず確認する
     * (別会社の部署IDを指定しても紐付けられない=⑧の要件)。
     */
    @Transactional
    public EmployeeDetailDto updateDepartment(Long companyId, Long targetUserId, Long newDepartmentId) {
        AppUser user = findOwned(companyId, targetUserId);
        if (newDepartmentId != null) {
            departmentService.requireOwned(companyId, newDepartmentId);
        }
        user.setDepartmentId(newDepartmentId);
        appUserRepository.save(user);
        return getEmployeeDetail(companyId, targetUserId);
    }

    /**
     * 基本情報(氏名・役職・メールアドレス)の編集。権限・所属部署は専用エンドポイントで扱う。
     * ログインID・パスワードはここでは変更しない(スコープ外。必要になれば別途エンドポイントを設ける)。
     * メールアドレスはセルフサービス型パスワードリセット(ForgotPasswordService)に使われる
     * ため、既存ユーザーの後埋め・修正ができるようにここに含めている。
     */
    @Transactional
    public EmployeeDetailDto updateProfile(Long companyId, Long targetUserId, EmployeeUpdateRequestDto dto) {
        AppUser user = findOwned(companyId, targetUserId);
        user.setFullName(dto.getFullName().trim());
        user.setPosition(blankToNull(dto.getPosition()));
        user.setEmail(blankToNull(dto.getEmail()));
        appUserRepository.save(user);
        return getEmployeeDetail(companyId, targetUserId);
    }

    /**
     * 従業員の削除。
     * ・自分自身の削除は禁止(誤操作でログインできなくなる事故防止。権限変更と同じ考え方)。
     * ・削除対象がADMINである場合、それによってログイン可能な管理者がMIN_ACTIVE_ADMINS人を
     *   下回る場合は禁止する(会社が誰も管理できなくなる/唯一の管理者が孤立する状態を防ぐ)。
     */
    @Transactional
    public void deleteEmployee(Long companyId, Long adminUserId, Long targetUserId) {
        if (adminUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自分自身を削除することはできません");
        }
        AppUser user = findOwned(companyId, targetUserId);
        if (user.getRole() == UserRole.ADMIN && isEnabled(user)) {
            requireAdminSurplus(companyId, "管理者の人数が%d名を下回るため削除できません。先に他の従業員を管理者にしてください。");
        }
        appUserRepository.delete(user);
    }

    /**
     * 管理者による強制パスワードリセット。対象社員の新しい一時パスワードを生成してBCryptハッシュ化のみ保存し、
     * 平文はこのメソッドの戻り値(=API応答)としてのみ一度だけ返す(DB・ログには一切残さない)。
     * リセット後は対象社員のmustChangePasswordがtrueになり、次回ログイン後はパスワード変更画面を
     * 通過するまで他の画面へ進めなくなる(MustChangePasswordFilter参照)。
     * 実際の生成・ハッシュ化・監査ログ記録はPasswordServiceに委譲する(将来のセルフサービスリセットからも
     * 再利用できるよう、コアロジックを管理者操作専用に固定化しないため。PasswordServiceのjavadoc参照)。
     */
    @Transactional
    public PasswordResetResultDto resetPassword(Long companyId, AppUser admin, Long targetUserId) {
        AppUser target = findOwned(companyId, targetUserId);
        String temporaryPassword = passwordService.resetPassword(
                target, admin.effectiveEmployeeId(), admin.getFullName());
        return PasswordResetResultDto.builder()
                .userId(target.getId())
                .loginId(target.getLoginId())
                .fullName(target.getFullName())
                .temporaryPassword(temporaryPassword)
                .build();
    }

    /** 他社ユーザーへ到達させないための共通ルックアップ */
    private AppUser findOwned(Long companyId, Long userId) {
        return appUserRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定の従業員が見つかりません"));
    }

    /**
     * 「この操作を行うと、ログイン可能な管理者がMIN_ACTIVE_ADMINS人を下回る」場合に例外を投げる。
     * 呼び出し時点ではまだ対象ユーザーの変更をDBへ反映していない前提(=現在の実際の人数を
     * そのまま数えれば、対象ユーザー自身を含んだ「操作前の人数」が取れる)。
     */
    private void requireAdminSurplus(Long companyId, String messageFormat) {
        if (countActiveAdmins(companyId) <= MIN_ACTIVE_ADMINS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format(messageFormat, MIN_ACTIVE_ADMINS));
        }
    }

    /** ログイン可能な(無効化されていない)ADMINの人数 */
    private long countActiveAdmins(Long companyId) {
        return appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId).stream()
                .filter(u -> u.getRole() == UserRole.ADMIN && isEnabled(u))
                .count();
    }

    /** enabled列がnullの既存行も有効として扱う(AppUserPrincipal#isEnabledと同じフェイルセーフ) */
    private boolean isEnabled(AppUser user) {
        return user.getEnabled() == null || user.getEnabled();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Map<Long, String> departmentNameMap(Long companyId) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .collect(java.util.stream.Collectors.toMap(Department::getId, Department::getName));
    }

    private EmployeeSummaryDto toSummaryDto(AppUser user, LocalDate today, Map<Long, String> departmentNames) {
        AttendanceRecordDto todayRecord = attendanceService.getMonth(user.effectiveEmployeeId(), today.getYear(), today.getMonthValue())
                .getDays().stream()
                .filter(d -> d.getWorkDate().equals(today))
                .findFirst()
                .orElse(null);

        String dayTypeLabel = todayRecord != null && todayRecord.getDayType() != null
                ? todayRecord.getDayType().getLabel() : null;
        String status = computeTodayStatus(todayRecord);

        return EmployeeSummaryDto.builder()
                .userId(user.getId())
                .loginId(user.getLoginId())
                .fullName(user.getFullName())
                .departmentId(user.getDepartmentId())
                .departmentName(user.getDepartmentId() == null ? null : departmentNames.get(user.getDepartmentId()))
                .position(user.getPosition())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .enabled(user.getEnabled() == null || user.getEnabled())
                .todayDayTypeLabel(dayTypeLabel)
                .todayStatus(status)
                .build();
    }

    /** 本日の勤務状況を簡易判定する(ダッシュボードの集計ロジックと同じ考え方) */
    static String computeTodayStatus(AttendanceRecordDto d) {
        if (d == null || d.getDayType() == null) return "未設定";
        boolean isWorkDay = d.getDayType() == DayType.NORMAL
                || d.getDayType() == DayType.SCHEDULED_HOLIDAY
                || d.getDayType() == DayType.STATUTORY_HOLIDAY;
        boolean clockedIn = d.getStartTime() != null;
        boolean clockedOut = d.getEndTime() != null;

        if (!isWorkDay) return d.getDayType().getLabel();
        if (clockedIn && clockedOut) return "退勤済み";
        if (clockedIn) return "出勤中";
        if (d.getDayType() == DayType.NORMAL) return "未出勤";
        return "休日"; // 所定/法定休日で出勤していない
    }
}
