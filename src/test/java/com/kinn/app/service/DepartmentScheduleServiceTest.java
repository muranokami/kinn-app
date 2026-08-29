package com.kinn.app.service;

import com.kinn.app.dto.DepartmentScheduleEventDto;
import com.kinn.app.dto.DepartmentScheduleListDto;
import com.kinn.app.dto.ScheduleFeedEventDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.ScheduleCategory;
import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.entity.ScheduleType;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DepartmentScheduleService(部署共有スケジュール②③⑥⑦⑨⑩⑪⑯⑰⑱⑳)のユニットテスト。
 * DBは使わず、会社単位・部署単位のアクセス制御(⑨⑩⑪。URLの数値IDを書き換えても
 * 他社・他部署のデータへ到達できないこと)と、「個人」「部署」「すべて」フィード合成(④⑬)を検証する。
 */
class DepartmentScheduleServiceTest {

    private ScheduleEventRepository scheduleEventRepository;
    private AppUserRepository appUserRepository;
    private DepartmentRepository departmentRepository;
    private DepartmentScheduleService service;

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long DEPT_SALES_A = 10L; // 会社Aの営業部
    private static final Long DEPT_DEV_A = 11L;   // 会社Aの開発部
    private static final Long DEPT_B = 20L;       // 会社Bの部署(会社Aからは見えないはず)

    @BeforeEach
    void setUp() {
        scheduleEventRepository = mock(ScheduleEventRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        DepartmentService departmentService = new DepartmentService(departmentRepository, appUserRepository);
        ScheduleService scheduleService = new ScheduleService(scheduleEventRepository);
        service = new DepartmentScheduleService(scheduleEventRepository, departmentService, scheduleService, appUserRepository);

        when(departmentRepository.findByIdAndCompanyId(DEPT_SALES_A, COMPANY_A))
                .thenReturn(Optional.of(dept(DEPT_SALES_A, COMPANY_A, "営業部")));
        when(departmentRepository.findById(DEPT_SALES_A))
                .thenReturn(Optional.of(dept(DEPT_SALES_A, COMPANY_A, "営業部")));
        // 会社Aの視点では会社Bの部署IDは存在しないものとして扱う(⑪他社データへの到達を防ぐ)
        when(departmentRepository.findByIdAndCompanyId(DEPT_B, COMPANY_A)).thenReturn(Optional.empty());
    }

    private Department dept(Long id, Long companyId, String name) {
        return Department.builder().id(id).companyId(companyId).name(name).build();
    }

    private AppUser user(Long companyId, Long departmentId) {
        return AppUser.builder().id(1L).companyId(companyId).loginId("yamada").fullName("山田太郎")
                .departmentId(departmentId).role(UserRole.USER).build();
    }

    private AppUser user(Long id, Long companyId, Long departmentId, String loginId, String fullName, UserRole role) {
        return AppUser.builder().id(id).companyId(companyId).loginId(loginId).fullName(fullName)
                .departmentId(departmentId).role(role).build();
    }

    // ------------------------------------------------------------------
    // 閲覧: 一般ユーザーは自分の部署の共有スケジュールを自動的に取得できる(⑥⑨㉒)
    // ------------------------------------------------------------------

    @Test
    void 一般ユーザーは自分の部署の共有スケジュールを取得できる() {
        AppUser yamada = user(COMPANY_A, DEPT_SALES_A);
        ScheduleEvent shared = ScheduleEvent.builder()
                .id(100L).employeeId(COMPANY_A + "|admin:9")
                .scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A)
                .eventDate(LocalDate.of(2026, 8, 25))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();
        when(scheduleEventRepository
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        eq(COMPANY_A), eq(DEPT_SALES_A), eq(ScheduleType.DEPARTMENT), any(), any()))
                .thenReturn(List.of(shared));

        DepartmentScheduleListDto dto = service.getMonthForUser(yamada, 2026, 8);

        assertEquals("営業部", dto.getDepartmentName());
        assertEquals(1, dto.getEvents().size());
        assertEquals("営業部定例会", dto.getEvents().get(0).getTitle());
    }

    @Test
    void 部署未所属のユーザーは空一覧が返る() {
        AppUser noDept = user(COMPANY_A, null);
        DepartmentScheduleListDto dto = service.getMonthForUser(noDept, 2026, 8);
        assertTrue(dto.getEvents().isEmpty());
        assertNull(dto.getDepartmentName());
        verifyNoInteractions(scheduleEventRepository);
    }

    @Test
    void 営業部ユーザーには開発部の共有予定は含まれない() {
        AppUser salesUser = user(COMPANY_A, DEPT_SALES_A);
        // 営業部(DEPT_SALES_A)で問い合わせても開発部の予定は返らない(クエリ自体がdepartmentIdで絞るため)
        when(scheduleEventRepository
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        eq(COMPANY_A), eq(DEPT_SALES_A), eq(ScheduleType.DEPARTMENT), any(), any()))
                .thenReturn(List.of());

        DepartmentScheduleListDto dto = service.getMonthForUser(salesUser, 2026, 8);

        assertTrue(dto.getEvents().isEmpty());
        verify(scheduleEventRepository, never())
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        eq(COMPANY_A), eq(DEPT_DEV_A), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // 会社単位のアクセス制御(⑪): URLの部署IDを書き換えただけでは他社データへ到達できない
    // ------------------------------------------------------------------

    @Test
    void 管理者が他社の部署IDを指定すると400になり取得できない() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMonthForAdmin(COMPANY_A, DEPT_B, 2026, 8));
        assertEquals(400, ex.getStatusCode().value());
        verify(scheduleEventRepository, never())
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        any(), any(), any(), any(), any());
    }

    @Test
    void 他社の部署IDを指定して登録しようとすると失敗する() {
        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("不正登録テスト").build();

        assertThrows(ResponseStatusException.class,
                () -> service.create(COMPANY_A, DEPT_B, 9L, dto));
        verify(scheduleEventRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 登録・編集・削除(⑦⑯⑰)。管理者のみ呼び出せる(AdminScheduleControllerがROLE_ADMIN限定)
    // ------------------------------------------------------------------

    @Test
    void 部署共有スケジュールを登録すると部署全体が閲覧できる状態で保存される() {
        when(scheduleEventRepository.save(any())).thenAnswer(inv -> {
            ScheduleEvent e = inv.getArgument(0);
            e.setId(200L);
            return e;
        });

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .title("営業部定例会").category(ScheduleCategory.MEETING)
                .content("月次営業会議").location("会議室A")
                .build();

        DepartmentScheduleEventDto saved = service.create(COMPANY_A, DEPT_SALES_A, 9L, dto);

        assertEquals("営業部定例会", saved.getTitle());
        assertEquals("営業部", saved.getDepartmentName());
        verify(scheduleEventRepository).save(argThat(e ->
                e.getScheduleType() == ScheduleType.DEPARTMENT
                        && e.getCompanyId().equals(COMPANY_A)
                        && e.getDepartmentId().equals(DEPT_SALES_A)
                        && e.getCreatedByUserId().equals(9L)));
    }

    @Test
    void 他社の部署の予定idを指定して削除しようとすると404になる() {
        when(scheduleEventRepository.findByIdAndCompanyIdAndDepartmentIdAndScheduleType(
                999L, COMPANY_A, DEPT_SALES_A, ScheduleType.DEPARTMENT)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.delete(COMPANY_A, DEPT_SALES_A, 999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // 一般ユーザー自身による部署共有スケジュールの登録・編集・削除(⑥⑭⑮⑯⑰⑱)
    // ------------------------------------------------------------------

    @Test
    void 一般ユーザーが部署共有スケジュールを登録できる() {
        AppUser sato = user(2L, COMPANY_A, DEPT_SALES_A, "sato", "佐藤花子", UserRole.USER);
        when(scheduleEventRepository.save(any())).thenAnswer(inv -> {
            ScheduleEvent e = inv.getArgument(0);
            e.setId(300L);
            return e;
        });

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25))
                .startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0))
                .title("営業部定例会").category(ScheduleCategory.MEETING)
                .content("月次営業会議").location("会議室A")
                .build();

        DepartmentScheduleEventDto saved = service.createForUser(sato, dto);

        assertEquals("営業部定例会", saved.getTitle());
        assertEquals("営業部", saved.getDepartmentName());
        verify(scheduleEventRepository).save(argThat(e ->
                e.getScheduleType() == ScheduleType.DEPARTMENT
                        && e.getCompanyId().equals(COMPANY_A)
                        && e.getDepartmentId().equals(DEPT_SALES_A)
                        && e.getCreatedByUserId().equals(2L)));
    }

    @Test
    void 部署未所属の一般ユーザーは部署共有スケジュールを登録できない() {
        AppUser noDept = user(2L, COMPANY_A, null, "sato", "佐藤花子", UserRole.USER);
        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("無所属登録テスト").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForUser(noDept, dto));
        assertEquals(400, ex.getStatusCode().value());
        verify(scheduleEventRepository, never()).save(any());
    }

    @Test
    void 登録者本人は自分が登録した部署共有スケジュールを編集できる() {
        AppUser sato = user(2L, COMPANY_A, DEPT_SALES_A, "sato", "佐藤花子", UserRole.USER);
        ScheduleEvent existing = ScheduleEvent.builder()
                .id(300L).employeeId(COMPANY_A + "|shared:2").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A).createdByUserId(2L)
                .eventDate(LocalDate.of(2026, 8, 25)).title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();
        when(scheduleEventRepository.findByIdAndCompanyIdAndScheduleType(300L, COMPANY_A, ScheduleType.DEPARTMENT))
                .thenReturn(Optional.of(existing));
        when(scheduleEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("営業部定例会(変更)").category(ScheduleCategory.MEETING)
                .build();

        DepartmentScheduleEventDto updated = service.updateOwn(sato, 300L, dto);
        assertEquals("営業部定例会(変更)", updated.getTitle());
    }

    @Test
    void 登録者本人でも管理者でもないユーザーは部署共有スケジュールを編集できない() {
        AppUser suzuki = user(3L, COMPANY_A, DEPT_SALES_A, "suzuki", "鈴木一郎", UserRole.USER);
        ScheduleEvent existing = ScheduleEvent.builder()
                .id(300L).employeeId(COMPANY_A + "|shared:2").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A).createdByUserId(2L)
                .eventDate(LocalDate.of(2026, 8, 25)).title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();
        when(scheduleEventRepository.findByIdAndCompanyIdAndScheduleType(300L, COMPANY_A, ScheduleType.DEPARTMENT))
                .thenReturn(Optional.of(existing));

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("乗っ取り").category(ScheduleCategory.MEETING).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateOwn(suzuki, 300L, dto));
        assertEquals(403, ex.getStatusCode().value());
        verify(scheduleEventRepository, never()).save(any());
    }

    @Test
    void 管理者は他人が登録した部署共有スケジュールも編集削除できる() {
        AppUser admin = user(9L, COMPANY_A, DEPT_SALES_A, "yamada", "山田太郎", UserRole.ADMIN);
        ScheduleEvent existing = ScheduleEvent.builder()
                .id(300L).employeeId(COMPANY_A + "|shared:2").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A).createdByUserId(2L)
                .eventDate(LocalDate.of(2026, 8, 25)).title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();
        when(scheduleEventRepository.findByIdAndCompanyIdAndScheduleType(300L, COMPANY_A, ScheduleType.DEPARTMENT))
                .thenReturn(Optional.of(existing));
        when(scheduleEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("管理者による修正").category(ScheduleCategory.MEETING).build();

        DepartmentScheduleEventDto updated = service.updateOwn(admin, 300L, dto);
        assertEquals("管理者による修正", updated.getTitle());

        service.deleteOwn(admin, 300L);
        verify(scheduleEventRepository).delete(existing);
    }

    @Test
    void 管理者は全部署横断で部署共有スケジュールを一度に取得できる() {
        when(departmentRepository.findByCompanyIdOrderByNameAsc(COMPANY_A))
                .thenReturn(List.of(dept(DEPT_SALES_A, COMPANY_A, "営業部"), dept(DEPT_DEV_A, COMPANY_A, "開発部")));

        ScheduleEvent salesEvent = ScheduleEvent.builder()
                .id(300L).employeeId(COMPANY_A + "|shared:2").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A).createdByUserId(2L)
                .eventDate(LocalDate.of(2026, 8, 25)).startTime(LocalTime.of(10, 0))
                .title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();
        ScheduleEvent devEvent = ScheduleEvent.builder()
                .id(301L).employeeId(COMPANY_A + "|shared:3").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_DEV_A).createdByUserId(3L)
                .eventDate(LocalDate.of(2026, 8, 26)).startTime(LocalTime.of(11, 0))
                .title("開発定例会").category(ScheduleCategory.MEETING)
                .build();
        when(scheduleEventRepository.findByCompanyIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                eq(COMPANY_A), eq(ScheduleType.DEPARTMENT), any(), any()))
                .thenReturn(List.of(salesEvent, devEvent));

        DepartmentScheduleListDto dto = service.getMonthForAdminAllDepartments(COMPANY_A, 2026, 8);

        assertEquals(2, dto.getEvents().size());
        assertEquals("営業部", dto.getEvents().get(0).getDepartmentName());
        assertEquals("開発部", dto.getEvents().get(1).getDepartmentName());
        // 単一部署の一覧ではないため、レスポンス全体のdepartmentId/departmentNameはnull
        assertNull(dto.getDepartmentId());
        assertNull(dto.getDepartmentName());
    }

    @Test
    void 他社のイベントIDを指定して編集しようとすると404になる() {
        AppUser companyBUser = user(5L, COMPANY_B, DEPT_B, "suzuki", "鈴木一郎", UserRole.ADMIN);
        when(scheduleEventRepository.findByIdAndCompanyIdAndScheduleType(300L, COMPANY_B, ScheduleType.DEPARTMENT))
                .thenReturn(Optional.empty());

        DepartmentScheduleEventDto dto = DepartmentScheduleEventDto.builder()
                .eventDate(LocalDate.of(2026, 8, 25)).title("他社から編集テスト").category(ScheduleCategory.MEETING).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateOwn(companyBUser, 300L, dto));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // 「個人」「部署」「すべて」フィード合成(④⑫⑬)
    // ------------------------------------------------------------------

    @Test
    void すべてフィードは個人予定と部署共有予定を日時順に合成する() {
        AppUser yamada = user(COMPANY_A, DEPT_SALES_A);
        String employeeId = yamada.effectiveEmployeeId();

        ScheduleEvent personal = ScheduleEvent.builder()
                .id(1L).employeeId(employeeId).scheduleType(ScheduleType.PERSONAL)
                .eventDate(LocalDate.of(2026, 8, 25)).startTime(LocalTime.of(9, 0))
                .title("顧客訪問").category(ScheduleCategory.WORK).memo("A社")
                .build();
        ScheduleEvent shared = ScheduleEvent.builder()
                .id(2L).employeeId(COMPANY_A + "|admin:9").scheduleType(ScheduleType.DEPARTMENT)
                .companyId(COMPANY_A).departmentId(DEPT_SALES_A)
                .eventDate(LocalDate.of(2026, 8, 25)).startTime(LocalTime.of(14, 0))
                .title("営業部定例会").category(ScheduleCategory.MEETING)
                .build();

        when(scheduleEventRepository.findByEmployeeIdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                eq(employeeId), any(), any())).thenReturn(List.of(personal));
        when(scheduleEventRepository
                .findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
                        eq(COMPANY_A), eq(DEPT_SALES_A), eq(ScheduleType.DEPARTMENT), any(), any()))
                .thenReturn(List.of(shared));

        List<ScheduleFeedEventDto> feed = service.getAllFeed(yamada, employeeId, 2026, 8);

        assertEquals(2, feed.size());
        assertEquals(ScheduleType.PERSONAL, feed.get(0).getScheduleType());
        assertEquals("顧客訪問", feed.get(0).getTitle());
        assertEquals(ScheduleType.DEPARTMENT, feed.get(1).getScheduleType());
        assertEquals("営業部定例会", feed.get(1).getTitle());
        assertEquals("営業部", feed.get(1).getDepartmentName());
    }
}
