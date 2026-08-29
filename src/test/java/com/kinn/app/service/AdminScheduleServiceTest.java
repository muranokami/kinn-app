package com.kinn.app.service;

import com.kinn.app.dto.AdminScheduleListDto;
import com.kinn.app.dto.ScheduleEventDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.ScheduleCategory;
import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import com.kinn.app.repository.ScheduleEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * AdminScheduleService の部署別・会社単位のアクセス制御(⑨⑩)と、
 * ScheduleServiceのCRUDロジック再利用(⑫⑬⑭)を検証するユニットテスト。DBは使わない。
 */
class AdminScheduleServiceTest {

    private ScheduleEventRepository scheduleEventRepository;
    private AppUserRepository appUserRepository;
    private DepartmentRepository departmentRepository;
    private AdminScheduleService service;

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long DEPT_SALES_A = 10L; // 会社Aの営業部
    private static final Long DEPT_DEV_A = 11L;   // 会社Aの開発部
    private static final Long DEPT_B = 20L;       // 会社Bの部署

    @BeforeEach
    void setUp() {
        scheduleEventRepository = mock(ScheduleEventRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        DepartmentService departmentService = new DepartmentService(departmentRepository, appUserRepository);
        ScheduleService scheduleService = new ScheduleService(scheduleEventRepository);
        service = new AdminScheduleService(scheduleService, scheduleEventRepository,
                appUserRepository, departmentRepository, departmentService);

        when(departmentRepository.findByIdAndCompanyId(DEPT_SALES_A, COMPANY_A))
                .thenReturn(Optional.of(dept(DEPT_SALES_A, COMPANY_A, "営業部")));
        when(departmentRepository.findByIdAndCompanyId(DEPT_DEV_A, COMPANY_A))
                .thenReturn(Optional.of(dept(DEPT_DEV_A, COMPANY_A, "開発部")));
        // 会社Aの管理者が会社Bの部署IDを指定した場合はヒットしない(⑨会社単位のアクセス制御)
        when(departmentRepository.findByIdAndCompanyId(DEPT_B, COMPANY_A)).thenReturn(Optional.empty());
        when(departmentRepository.findByCompanyIdOrderByNameAsc(COMPANY_A))
                .thenReturn(List.of(dept(DEPT_SALES_A, COMPANY_A, "営業部"), dept(DEPT_DEV_A, COMPANY_A, "開発部")));
    }

    private Department dept(Long id, Long companyId, String name) {
        return Department.builder().id(id).companyId(companyId).name(name).build();
    }

    private AppUser user(Long id, Long companyId, String loginId, String fullName, Long departmentId) {
        return AppUser.builder().id(id).companyId(companyId).loginId(loginId).fullName(fullName)
                .departmentId(departmentId).role(UserRole.USER).build();
    }

    @Test
    void 部署を指定すると同じ部署のユーザーの予定だけが返る() {
        AppUser yamada = user(1L, COMPANY_A, "yamada", "山田太郎", DEPT_SALES_A);
        AppUser sato = user(2L, COMPANY_A, "sato", "佐藤花子", DEPT_SALES_A);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));

        ScheduleEvent event = ScheduleEvent.builder()
                .id(100L).employeeId(yamada.effectiveEmployeeId())
                .eventDate(LocalDate.of(2026, 8, 25))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .title("営業会議").category(ScheduleCategory.MEETING).build();
        when(scheduleEventRepository.findByEmployeeIdInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                anyCollection(), any(), any())).thenReturn(List.of(event));

        AdminScheduleListDto dto = service.getMonth(COMPANY_A, 2026, 8, DEPT_SALES_A);

        assertEquals("営業部", dto.getDepartmentName());
        assertEquals(1, dto.getEvents().size());
        assertEquals("山田太郎", dto.getEvents().get(0).getFullName());
        assertEquals("火", dto.getEvents().get(0).getDayOfWeekLabel());
        assertEquals("会議", dto.getEvents().get(0).getCategoryLabel());
    }

    @Test
    void 部署未指定なら会社全体のユーザーが対象になる() {
        when(appUserRepository.findByCompanyIdOrderByFullNameAsc(COMPANY_A))
                .thenReturn(List.of(user(1L, COMPANY_A, "yamada", "山田太郎", DEPT_SALES_A),
                        user(3L, COMPANY_A, "suzuki", "鈴木一郎", DEPT_DEV_A)));
        when(scheduleEventRepository.findByEmployeeIdInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                anyCollection(), any(), any())).thenReturn(List.of());

        AdminScheduleListDto dto = service.getMonth(COMPANY_A, 2026, 8, null);

        assertNull(dto.getDepartmentName());
        verify(appUserRepository).findByCompanyIdOrderByFullNameAsc(COMPANY_A);
        verify(appUserRepository, never()).findByCompanyIdAndDepartmentIdOrderByFullNameAsc(any(), any());
    }

    @Test
    void 他社の部署IDを指定すると400になり会社Aの一覧取得はできない() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMonth(COMPANY_A, 2026, 8, DEPT_B));
        assertEquals(400, ex.getStatusCode().value());
        verify(scheduleEventRepository, never())
                .findByEmployeeIdInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(anyCollection(), any(), any());
    }

    @Test
    void 部署全員へのスケジュール一括登録は部署所属者ごとにScheduleServiceのcreateを呼ぶ() {
        AppUser yamada = user(1L, COMPANY_A, "yamada", "山田太郎", DEPT_SALES_A);
        AppUser sato = user(2L, COMPANY_A, "sato", "佐藤花子", DEPT_SALES_A);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));
        when(scheduleEventRepository.save(any())).thenAnswer(inv -> {
            ScheduleEvent e = inv.getArgument(0);
            e.setId(999L);
            return e;
        });

        ScheduleEventDto dto = ScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .title("月次営業会議").category(ScheduleCategory.MEETING)
                .build();

        List<ScheduleEventDto> created = service.createForDepartment(COMPANY_A, DEPT_SALES_A, dto);

        assertEquals(2, created.size());
        verify(scheduleEventRepository, times(2)).save(any());
    }

    @Test
    void 他社の従業員IDを指定して編集しようとすると404になる() {
        when(appUserRepository.findByIdAndCompanyId(999L, COMPANY_A)).thenReturn(Optional.empty());

        ScheduleEventDto dto = ScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("不正アクセステスト")
                .category(ScheduleCategory.OTHER).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateForEmployee(COMPANY_A, 999L, 1L, dto));
        assertEquals(404, ex.getStatusCode().value());
    }
}
