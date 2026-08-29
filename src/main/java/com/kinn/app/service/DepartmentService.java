package com.kinn.app.service;

import com.kinn.app.dto.DepartmentCreateRequestDto;
import com.kinn.app.dto.DepartmentDto;
import com.kinn.app.entity.Department;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部署管理(会社単位)。Company → Department → AppUser という階層のうち、
 * Department部分を一元的に扱う。すべてのメソッドが companyId を条件に含めるため、
 * 他社の部署IDを指定しても到達できない(会社単位のアクセス制御)。
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository appUserRepository;

    public DepartmentService(DepartmentRepository departmentRepository, AppUserRepository appUserRepository) {
        this.departmentRepository = departmentRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> getDepartments(Long companyId) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(d -> DepartmentDto.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .employeeCount(appUserRepository.countByCompanyIdAndDepartmentId(companyId, d.getId()))
                        .build())
                .toList();
    }

    /** 部署名の一覧のみ(ログイン前の新規登録画面が使う。公開APIなので個人情報は一切含まない) */
    @Transactional(readOnly = true)
    public List<String> getDepartmentNames(Long companyId) {
        return departmentRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(Department::getName)
                .toList();
    }

    @Transactional
    public DepartmentDto createDepartment(Long companyId, DepartmentCreateRequestDto dto) {
        String name = dto.getName().trim();
        if (departmentRepository.existsByCompanyIdAndName(companyId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "この部署名は既に登録されています。");
        }
        Department saved = departmentRepository.save(
                Department.builder().companyId(companyId).name(name).build());
        return DepartmentDto.builder().id(saved.getId()).name(saved.getName()).employeeCount(0).build();
    }

    /** 指定の部署が本当にその会社に属しているか確認する(会社単位のアクセス制御) */
    @Transactional(readOnly = true)
    public Department requireOwned(Long companyId, Long departmentId) {
        return departmentRepository.findByIdAndCompanyId(departmentId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定の部署が見つかりません"));
    }

    @Transactional(readOnly = true)
    public String getDepartmentNameOrNull(Long departmentId) {
        if (departmentId == null) return null;
        return departmentRepository.findById(departmentId).map(Department::getName).orElse(null);
    }

    /**
     * 会社内の全部署のID→部署名。管理者が「全部署」を横断して一覧表示する画面
     * (会社全体の部署共有スケジュール等)で、行ごとに部署名を出すために使う。
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getDepartmentNameMap(Long companyId) {
        Map<Long, String> map = new HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByNameAsc(companyId)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    /**
     * 新規登録画面用: 会社名から部署を解決する。
     * ・その会社に部署が1件以上既にある場合: 登録済みの部署名と完全一致するものだけを許可する
     *   (存在しない部署名では登録できない)。
     * ・その会社にまだ部署が1件も無い場合(会社が今回新規作成された場合も含む): 最初のユーザー
     *   なので、入力された部署名で最初の部署を新規作成する(でなければ、部署が1件も無い会社には
     *   誰も登録できなくなってしまうため)。
     *
     * @param allowNewDepartment true の場合、部署名が既存に見つからなければ新規作成する。
     *                           呼び出し側(AuthService)は「その会社に部署が1件も無いか」で判定する
     *                           (会社の新規/既存だけでは判定しない。会社は既に存在するが部署が
     *                           まだ無いケースもあり得るため)。
     */
    @Transactional
    public Department resolveForRegistration(Long companyId, String departmentName, boolean allowNewDepartment) {
        String name = departmentName.trim();
        if (allowNewDepartment) {
            return departmentRepository.findByCompanyIdAndName(companyId, name)
                    .orElseGet(() -> departmentRepository.save(
                            Department.builder().companyId(companyId).name(name).build()));
        }
        return departmentRepository.findByCompanyIdAndName(companyId, name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "指定された部署は存在しません。管理者に部署の追加を依頼してください。"));
    }
}
