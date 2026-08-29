package com.kinn.app.service;

import com.kinn.app.dto.AdminTaskListDto;
import com.kinn.app.dto.DepartmentDayTaskDto;
import com.kinn.app.dto.MyTaskBoardDto;
import com.kinn.app.dto.TaskAssigneeOptionDto;
import com.kinn.app.dto.TaskDto;
import com.kinn.app.dto.TaskProgressRowDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.Task;
import com.kinn.app.entity.TaskPriority;
import com.kinn.app.entity.TaskStatus;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import com.kinn.app.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskService の権限・アクセス制御(㉑㉒㉓㊲㊳)と業務ロジック(④⑤⑮⑯)を検証するユニットテスト。
 * DBは使わない(AdminScheduleServiceTestと同じ方式。AppUserRepository/DepartmentRepositoryを
 * モック化し、DepartmentServiceは実体をそのまま使う)。
 */
class TaskServiceTest {

    private TaskRepository taskRepository;
    private AppUserRepository appUserRepository;
    private DepartmentRepository departmentRepository;
    private TaskService service;

    private static final Long COMPANY_A = 1L;
    private static final Long COMPANY_B = 2L;
    private static final Long DEPT_SALES_A = 10L; // 会社Aの営業部
    private static final Long DEPT_DEV_A = 11L;   // 会社Aの開発部
    private static final Long DEPT_B = 20L;       // 会社Bの部署

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        appUserRepository = mock(AppUserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        DepartmentService departmentService = new DepartmentService(departmentRepository, appUserRepository);
        service = new TaskService(taskRepository, appUserRepository, departmentService);

        when(departmentRepository.findByIdAndCompanyId(DEPT_SALES_A, COMPANY_A))
                .thenReturn(Optional.of(dept(DEPT_SALES_A, COMPANY_A, "営業部")));
        when(departmentRepository.findByIdAndCompanyId(DEPT_DEV_A, COMPANY_A))
                .thenReturn(Optional.of(dept(DEPT_DEV_A, COMPANY_A, "開発部")));
        when(departmentRepository.findByIdAndCompanyId(DEPT_B, COMPANY_A)).thenReturn(Optional.empty());
        when(departmentRepository.findById(DEPT_SALES_A)).thenReturn(Optional.of(dept(DEPT_SALES_A, COMPANY_A, "営業部")));
        when(departmentRepository.findByCompanyIdOrderByNameAsc(COMPANY_A))
                .thenReturn(List.of(dept(DEPT_SALES_A, COMPANY_A, "営業部"), dept(DEPT_DEV_A, COMPANY_A, "開発部")));
    }

    private Department dept(Long id, Long companyId, String name) {
        return Department.builder().id(id).companyId(companyId).name(name).build();
    }

    private AppUser user(Long id, Long companyId, String fullName, Long departmentId, UserRole role) {
        return AppUser.builder().id(id).companyId(companyId).loginId("u" + id).fullName(fullName)
                .departmentId(departmentId).role(role).build();
    }

    private Task task(Long id, Long companyId, Long departmentId, Long assignedUserId, Long createdByUserId,
                       TaskStatus status, LocalDate dueDate) {
        return Task.builder().id(id).companyId(companyId).departmentId(departmentId)
                .assignedUserId(assignedUserId).createdByUserId(createdByUserId)
                .title("タスク" + id).status(status).priority(TaskPriority.MEDIUM).dueDate(dueDate).build();
    }

    // ------------------------------------------------------------------
    // 一般ユーザー(㉓)
    // ------------------------------------------------------------------

    @Test
    void 自分に割り当てられたタスクが未対応対応中完了に分類される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.findByAssignedUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),
                task(101L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, null),
                task(102L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.COMPLETED, null)));
        when(appUserRepository.findByCompanyIdOrderByFullNameAsc(COMPANY_A)).thenReturn(List.of(yamada));

        MyTaskBoardDto board = service.getMyBoard(yamada);

        assertEquals(1, board.getUnresolved().size());
        assertEquals(1, board.getInProgress().size());
        assertEquals(1, board.getCompleted().size());
        assertEquals(3, board.getSummary().getTotalCount());
    }

    @Test
    void 一般ユーザーが登録したタスクは未対応かつ自分自身が担当者になる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.save(any())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(200L);
            return t;
        });

        TaskDto input = TaskDto.builder()
                .title("資料作成").priority(TaskPriority.HIGH).dueDate(LocalDate.of(2026, 8, 26))
                // 不正な部署情報を混入させても無視されることを確認する(⑨⑮㊳)。
                // 担当者(assignedUserId)は②により同じ部署内なら正式な入力項目になったため、
                // ここでは指定しない(省略時に本人になることは別途確認する)。
                .departmentId(DEPT_DEV_A)
                .build();

        TaskDto result = service.createForSelf(yamada, input);

        assertEquals(TaskStatus.UNRESOLVED, result.getStatus());
        assertEquals(1L, result.getAssignedUserId());
        verify(taskRepository).save(argThat(t ->
                t.getCompanyId().equals(COMPANY_A)
                        && t.getDepartmentId().equals(DEPT_SALES_A) // dto.departmentIdは無視され本人の部署になる
                        && t.getAssignedUserId().equals(1L)         // 省略時は本人が担当者になる
                        && t.getCreatedByUserId().equals(1L)));
    }

    @Test
    void 部署未所属の一般ユーザーはタスクを登録できない() {
        AppUser noDept = user(1L, COMPANY_A, "無所属太郎", null, UserRole.USER);
        TaskDto input = TaskDto.builder().title("タスク").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForSelf(noDept, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void ステータスを対応中に変更できる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskDto result = service.updateStatusOwn(yamada, 100L, TaskStatus.IN_PROGRESS);

        assertEquals(TaskStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void ステータスを完了に変更できる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskDto result = service.updateStatusOwn(yamada, 100L, TaskStatus.COMPLETED);

        assertEquals(TaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    void 自分のタスクの仕事内容とステータスと備考を編集できるが所属情報は変更されない() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskDto input = TaskDto.builder()
                .title("更新後タイトル").description("更新後の仕事内容").notes("更新後の備考")
                .status(TaskStatus.IN_PROGRESS)
                .departmentId(DEPT_DEV_A).assignedUserId(999L) // 送っても無視される
                .build();

        TaskDto result = service.updateOwn(yamada, 100L, input);

        assertEquals("更新後の仕事内容", result.getDescription());
        assertEquals("更新後の備考", result.getNotes());
        assertEquals(TaskStatus.IN_PROGRESS, result.getStatus());
        assertEquals(DEPT_SALES_A, result.getDepartmentId());
        assertEquals(1L, result.getAssignedUserId());
    }

    @Test
    void 他人のタスクを一般ユーザーが編集できない() {
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null); // 山田のタスク
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        TaskDto input = TaskDto.builder().title("不正編集").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateOwn(sato, 100L, input));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void 自分で作成したタスクを削除できる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        service.deleteOwn(yamada, 100L);

        verify(taskRepository).delete(t);
    }

    @Test
    void 管理者が割り当てただけのタスクは担当者本人でも削除できない() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        // createdByUserId=99(管理者)。assignedUserId=1(山田=担当者本人)だが登録者ではない
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 99L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteOwn(yamada, 100L));
        assertEquals(403, ex.getStatusCode().value());
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void 他社のタスクIDを指定すると一般ユーザーからは見つからない() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.findByIdAndCompanyId(999L, COMPANY_A)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getDetailForUser(yamada, 999L));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // 管理者(⑩〜⑯⑲⑳㉖㉗㉘)
    // ------------------------------------------------------------------

    @Test
    void 管理者は部署を指定してその部署のタスクだけ確認できる() {
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, null)));

        AdminTaskListDto dto = service.getForAdmin(COMPANY_A, DEPT_SALES_A, null, null, null, null, null);

        assertEquals("営業部", dto.getDepartmentName());
        assertEquals(1, dto.getTasks().size());
    }

    @Test
    void 管理者はユーザー別ステータス別に絞り込める() {
        when(taskRepository.findByCompanyIdOrderByCreatedAtDesc(COMPANY_A)).thenReturn(List.of(
                task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),
                task(2L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, null),
                task(3L, COMPANY_A, DEPT_SALES_A, 2L, 1L, TaskStatus.IN_PROGRESS, null)));

        AdminTaskListDto byUser = service.getForAdmin(COMPANY_A, null, 1L, null, null, null, null);
        assertEquals(2, byUser.getTasks().size());

        AdminTaskListDto byStatus = service.getForAdmin(COMPANY_A, null, null, null, TaskStatus.IN_PROGRESS, null, null);
        assertEquals(2, byStatus.getTasks().size());
    }

    @Test
    void 管理者がタスクを登録すると自社かつ指定部署の担当者に割り当てられる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByIdAndCompanyId(1L, COMPANY_A)).thenReturn(Optional.of(yamada));
        when(taskRepository.save(any())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(300L);
            return t;
        });

        TaskDto input = TaskDto.builder()
                .departmentId(DEPT_SALES_A).assignedUserId(1L)
                .title("A社見積書作成").priority(TaskPriority.HIGH)
                .startDate(LocalDate.of(2026, 8, 23)).dueDate(LocalDate.of(2026, 8, 25))
                .build();

        TaskDto result = service.createByAdmin(COMPANY_A, 99L, input);

        assertEquals(DEPT_SALES_A, result.getDepartmentId());
        assertEquals(1L, result.getAssignedUserId());
        assertEquals(TaskStatus.UNRESOLVED, result.getStatus());
    }

    @Test
    void 別部署のユーザーを担当者に指定するとエラーになる() {
        // 鈴木は開発部所属なのに、営業部のタスクとして割り当てようとする
        AppUser suzuki = user(3L, COMPANY_A, "鈴木一郎", DEPT_DEV_A, UserRole.USER);
        when(appUserRepository.findByIdAndCompanyId(3L, COMPANY_A)).thenReturn(Optional.of(suzuki));

        TaskDto input = TaskDto.builder().departmentId(DEPT_SALES_A).assignedUserId(3L).title("不正割当").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createByAdmin(COMPANY_A, 99L, input));
        assertEquals(400, ex.getStatusCode().value());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void 別会社のユーザーを担当者に指定するとエラーになる() {
        when(appUserRepository.findByIdAndCompanyId(777L, COMPANY_A)).thenReturn(Optional.empty());

        TaskDto input = TaskDto.builder().departmentId(DEPT_SALES_A).assignedUserId(777L).title("不正割当").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createByAdmin(COMPANY_A, 99L, input));
        assertEquals(400, ex.getStatusCode().value());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void 他社の部署IDを指定してタスクを登録しようとすると400になる() {
        TaskDto input = TaskDto.builder().departmentId(DEPT_B).assignedUserId(1L).title("越権タスク").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createByAdmin(COMPANY_A, 99L, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 管理者はタスクを編集できる() {
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(appUserRepository.findByIdAndCompanyId(1L, COMPANY_A)).thenReturn(Optional.of(yamada));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(yamada));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskDto input = TaskDto.builder()
                .title("編集後タイトル").status(TaskStatus.COMPLETED).priority(TaskPriority.LOW).build();

        TaskDto result = service.updateByAdmin(COMPANY_A, 100L, input);

        assertEquals("編集後タイトル", result.getTitle());
        assertEquals(TaskStatus.COMPLETED, result.getStatus());
    }

    @Test
    void 管理者はタスクの担当者を同じ部署の別ユーザーに変更できる() {
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null); // 現在の担当=山田(userId=1)
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(appUserRepository.findByIdAndCompanyId(2L, COMPANY_A)).thenReturn(Optional.of(sato));
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(sato));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskDto input = TaskDto.builder()
                .departmentId(DEPT_SALES_A).assignedUserId(2L) // 佐藤へ担当者変更(⑭)
                .title("A社見積書作成").build();

        TaskDto result = service.updateByAdmin(COMPANY_A, 100L, input);

        assertEquals(2L, result.getAssignedUserId());
        assertEquals("佐藤花子", result.getAssignedUserName());
    }

    @Test
    void 管理者が担当者を別部署のユーザーに変更しようとするとエラーになる() {
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        AppUser suzuki = user(3L, COMPANY_A, "鈴木一郎", DEPT_DEV_A, UserRole.USER); // 開発部
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(appUserRepository.findByIdAndCompanyId(3L, COMPANY_A)).thenReturn(Optional.of(suzuki));

        TaskDto input = TaskDto.builder()
                .departmentId(DEPT_SALES_A).assignedUserId(3L) // 営業部のタスクに開発部の鈴木を指定
                .title("A社見積書作成").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateByAdmin(COMPANY_A, 100L, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 他社のタスクは管理者でも編集削除できない() {
        when(taskRepository.findByIdAndCompanyId(999L, COMPANY_A)).thenReturn(Optional.empty());

        TaskDto input = TaskDto.builder().title("越権編集").build();
        ResponseStatusException editEx = assertThrows(ResponseStatusException.class,
                () -> service.updateByAdmin(COMPANY_A, 999L, input));
        assertEquals(404, editEx.getStatusCode().value());

        ResponseStatusException deleteEx = assertThrows(ResponseStatusException.class,
                () -> service.deleteByAdmin(COMPANY_A, 999L));
        assertEquals(404, deleteEx.getStatusCode().value());
    }

    @Test
    void 管理者はタスクを削除できる() {
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        service.deleteByAdmin(COMPANY_A, 100L);

        verify(taskRepository).delete(t);
    }

    @Test
    void ユーザー別部署別の進捗が正しく集計される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),
                        task(2L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.COMPLETED, null),
                        task(3L, COMPANY_A, DEPT_SALES_A, 2L, 1L, TaskStatus.IN_PROGRESS, null)));

        List<TaskProgressRowDto> rows = service.getUserProgress(COMPANY_A, DEPT_SALES_A);

        TaskProgressRowDto yamadaRow = rows.stream().filter(r -> r.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(1, yamadaRow.getUnresolvedCount());
        assertEquals(1, yamadaRow.getCompletedCount());

        TaskProgressRowDto satoRow = rows.stream().filter(r -> r.getId().equals(2L)).findFirst().orElseThrow();
        assertEquals(1, satoRow.getInProgressCount());
    }

    @Test
    void 部署別進捗が会社全体で正しく集計される() {
        when(taskRepository.findByCompanyIdOrderByCreatedAtDesc(COMPANY_A)).thenReturn(List.of(
                task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),
                task(2L, COMPANY_A, DEPT_DEV_A, 3L, 1L, TaskStatus.COMPLETED, null),
                task(3L, COMPANY_A, DEPT_DEV_A, 3L, 1L, TaskStatus.COMPLETED, null)));

        List<TaskProgressRowDto> rows = service.getDepartmentProgress(COMPANY_A);

        TaskProgressRowDto sales = rows.stream().filter(r -> r.getId().equals(DEPT_SALES_A)).findFirst().orElseThrow();
        assertEquals(1, sales.getUnresolvedCount());
        TaskProgressRowDto dev = rows.stream().filter(r -> r.getId().equals(DEPT_DEV_A)).findFirst().orElseThrow();
        assertEquals(2, dev.getCompletedCount());
    }

    // ------------------------------------------------------------------
    // 期限(⑮)・入力チェック(㊲)
    // ------------------------------------------------------------------

    @Test
    void 期限が今日より前かつ未完了のタスクは期限超過と判定される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(taskRepository.findByAssignedUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, LocalDate.now().minusDays(1)),
                task(2L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.COMPLETED, LocalDate.now().minusDays(1))));
        when(appUserRepository.findByCompanyIdOrderByFullNameAsc(COMPANY_A)).thenReturn(List.of(yamada));

        MyTaskBoardDto board = service.getMyBoard(yamada);

        assertTrue(board.getUnresolved().get(0).isOverdue());
        assertFalse(board.getCompleted().get(0).isOverdue()); // 完了済みは期限超過扱いしない
        assertEquals(1, board.getSummary().getOverdueCount());
    }

    @Test
    void タスク名が未入力だとエラーになる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        TaskDto input = TaskDto.builder().title("  ").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForSelf(yamada, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 期限が開始日より前だとエラーになる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        TaskDto input = TaskDto.builder().title("タスク")
                .startDate(LocalDate.of(2026, 8, 25)).dueDate(LocalDate.of(2026, 8, 20)).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForSelf(yamada, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // ②依頼者と担当者の分離
    // ------------------------------------------------------------------

    @Test
    void 依頼者と担当者を別々に設定できる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByIdAndCompanyId(2L, COMPANY_A)).thenReturn(Optional.of(sato));
        when(taskRepository.save(any())).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(400L);
            return t;
        });

        TaskDto input = TaskDto.builder().title("A社見積書作成").assignedUserId(2L).build();
        TaskDto result = service.createForSelf(yamada, input);

        assertEquals(2L, result.getAssignedUserId());       // 担当者=佐藤
        verify(taskRepository).save(argThat(t -> t.getCreatedByUserId().equals(1L))); // 依頼者=山田(本人固定)
    }

    @Test
    void 別部署のユーザーには依頼できない() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser suzuki = user(3L, COMPANY_A, "鈴木一郎", DEPT_DEV_A, UserRole.USER);
        when(appUserRepository.findByIdAndCompanyId(3L, COMPANY_A)).thenReturn(Optional.of(suzuki));

        TaskDto input = TaskDto.builder().title("不正依頼").assignedUserId(3L).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForSelf(yamada, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void 存在しない担当ユーザーを指定するとエラーになる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByIdAndCompanyId(888L, COMPANY_A)).thenReturn(Optional.empty());

        TaskDto input = TaskDto.builder().title("不正依頼").assignedUserId(888L).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createForSelf(yamada, input));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // ⑤⑥⑦部署内共有・閲覧権限
    // ------------------------------------------------------------------

    @Test
    void 同じ部署のユーザーは他人のタスクでも詳細を閲覧できる() {
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null); // 山田のタスク
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER)));

        TaskDto result = service.getDetailForUser(sato, 100L);

        assertEquals("タスク100", result.getTitle());
    }

    @Test
    void 別部署のユーザーはタスク詳細を閲覧できない() {
        AppUser suzuki = user(3L, COMPANY_A, "鈴木一郎", DEPT_DEV_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null);
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getDetailForUser(suzuki, 100L));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void 同じ部署でも他人のタスクは編集できない() {
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        Task t = task(100L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null); // 山田のタスク
        when(taskRepository.findByIdAndCompanyId(100L, COMPANY_A)).thenReturn(Optional.of(t));

        TaskDto input = TaskDto.builder().title("不正編集").build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateOwn(sato, 100L, input));
        assertEquals(403, ex.getStatusCode().value()); // 閲覧できても編集は不可(⑧)
    }

    @Test
    void 自分の部署のメンバー一覧を取得できる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));

        List<TaskAssigneeOptionDto> members = service.getDepartmentMembers(yamada);

        assertEquals(2, members.size());
    }

    @Test
    void 部署未所属ユーザーのメンバー一覧は空になる() {
        AppUser noDept = user(9L, COMPANY_A, "無所属", null, UserRole.USER);
        assertTrue(service.getDepartmentMembers(noDept).isEmpty());
    }

    // ------------------------------------------------------------------
    // ⑨⑩⑪⑬⑭⑮日別タスク表示
    // ------------------------------------------------------------------

    @Test
    void 今日のタスクが未対応対応中完了に分けて表示される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        LocalDate today = LocalDate.now();
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null, today),
                        taskWithRange(2L, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, today, today),
                        taskWithRange(3L, DEPT_SALES_A, 1L, 1L, TaskStatus.COMPLETED, today.minusDays(1), today.plusDays(1))));

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, today, null, null);

        assertEquals(1, dto.getUnresolved().size());
        assertEquals(1, dto.getInProgress().size());
        assertEquals(1, dto.getCompleted().size());
    }

    @Test
    void 前日と翌日を指定するとその日に該当するタスクだけが表示される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        LocalDate today = LocalDate.now();
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        // 期限のみ「今日」に設定されたタスク(開始日なし) → 今日だけに出現する単発タスク扱い
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null, today)));

        assertEquals(0, service.getDepartmentDay(yamada, today.minusDays(1), null, null).getSummary().getTotalCount());
        assertEquals(1, service.getDepartmentDay(yamada, today, null, null).getSummary().getTotalCount());
        assertEquals(0, service.getDepartmentDay(yamada, today.plusDays(1), null, null).getSummary().getTotalCount());
    }

    @Test
    void 開始日も期限も未設定のタスクは日別表示の対象外になる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null, null)));

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, LocalDate.now(), null, null);

        assertEquals(0, dto.getSummary().getTotalCount());
    }

    @Test
    void ユーザー別フィルターで担当者を絞り込める() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER);
        LocalDate today = LocalDate.now();
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, today, today),
                        taskWithRange(2L, DEPT_SALES_A, 2L, 1L, TaskStatus.UNRESOLVED, today, today)));

        DepartmentDayTaskDto onlyYamada = service.getDepartmentDay(yamada, today, 1L, null);
        assertEquals(1, onlyYamada.getSummary().getTotalCount());

        DepartmentDayTaskDto all = service.getDepartmentDay(yamada, today, null, null);
        assertEquals(2, all.getSummary().getTotalCount());
    }

    @Test
    void 部署内のメンバーはタスクがなくてもユーザー別グループに含まれる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        AppUser sato = user(2L, COMPANY_A, "佐藤花子", DEPT_SALES_A, UserRole.USER); // タスクなし
        LocalDate today = LocalDate.now();
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(sato, yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, today, today)));

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, today, null, null);

        assertEquals(2, dto.getByUser().size());
        var satoGroup = dto.getByUser().stream().filter(g -> g.getUserId().equals(2L)).findFirst().orElseThrow();
        assertTrue(satoGroup.getTasks().isEmpty());
    }

    @Test
    void 日付を指定しない場合は部署の全タスクが期間を問わず一覧表示される() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(2)),
                        taskWithRange(2L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null, null))); // 日付未設定

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, null, null, null);

        // ①部署別タスク一覧: 日付を指定しなければ、期限が遠い過去でも日付未設定でも全件表示される
        assertEquals(2, dto.getSummary().getTotalCount());
        assertNull(dto.getDate());
    }

    @Test
    void ステータスフィルターで絞り込める() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),
                        task(2L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.IN_PROGRESS, null)));

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, null, null, TaskStatus.IN_PROGRESS);

        assertEquals(1, dto.getSummary().getTotalCount());
        assertEquals(1, dto.getInProgress().size());
        assertEquals(0, dto.getUnresolved().size());
    }

    @Test
    void 担当者アカウントが削除されていても未割り当てと表示されエラーにならない() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        // 担当者(userId=999)が既に従業員削除されており、会社の全ユーザー一覧にも含まれない状況を再現する
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(appUserRepository.findByCompanyIdOrderByFullNameAsc(COMPANY_A)).thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(task(1L, COMPANY_A, DEPT_SALES_A, 999L, 1L, TaskStatus.UNRESOLVED, null)));

        DepartmentDayTaskDto dto = service.getDepartmentDay(yamada, null, null, null);

        assertEquals("未割り当て", dto.getUnresolved().get(0).getAssignedUserName());
    }

    @Test
    void 部署別タスク一覧の取得は会社IDと部署IDの組み合わせでのみ問い合わせる() {
        AppUser yamada = user(1L, COMPANY_A, "山田太郎", DEPT_SALES_A, UserRole.USER);
        when(appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(yamada));
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of());

        service.getDepartmentDay(yamada, null, null, null);

        // company_id + department_id の組み合わせでしか問い合わせないため、
        // 他社の同じdepartment_idの部署が偶然存在しても混入しない(⑤)
        verify(taskRepository).findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A);
        verify(taskRepository, never()).findByCompanyIdOrderByCreatedAtDesc(any());
    }

    @Test
    void 部署未所属ユーザーは日別タスクが空になる() {
        AppUser noDept = user(9L, COMPANY_A, "無所属", null, UserRole.USER);
        DepartmentDayTaskDto dto = service.getDepartmentDay(noDept, LocalDate.now(), null, null);
        assertNull(dto.getDepartmentId());
        assertEquals(0, dto.getSummary().getTotalCount());
    }

    // ------------------------------------------------------------------
    // ⑰⑱⑲⑳管理者: 依頼者・優先度・日付での絞り込み
    // ------------------------------------------------------------------

    @Test
    void 管理者は依頼者で絞り込める() {
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        task(1L, COMPANY_A, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, null),  // 依頼者=1
                        task(2L, COMPANY_A, DEPT_SALES_A, 2L, 99L, TaskStatus.UNRESOLVED, null))); // 依頼者=99(管理者)

        AdminTaskListDto dto = service.getForAdmin(COMPANY_A, DEPT_SALES_A, null, 99L, null, null, null);

        assertEquals(1, dto.getTasks().size());
        assertEquals(2L, dto.getTasks().get(0).getId());
    }

    @Test
    void 管理者は優先度で絞り込める() {
        when(taskRepository.findByCompanyIdOrderByCreatedAtDesc(COMPANY_A)).thenReturn(List.of(
                Task.builder().id(1L).companyId(COMPANY_A).departmentId(DEPT_SALES_A)
                        .assignedUserId(1L).createdByUserId(1L).title("高").status(TaskStatus.UNRESOLVED)
                        .priority(TaskPriority.HIGH).build(),
                Task.builder().id(2L).companyId(COMPANY_A).departmentId(DEPT_SALES_A)
                        .assignedUserId(1L).createdByUserId(1L).title("低").status(TaskStatus.UNRESOLVED)
                        .priority(TaskPriority.LOW).build()));

        AdminTaskListDto dto = service.getForAdmin(COMPANY_A, null, null, null, null, TaskPriority.HIGH, null);

        assertEquals(1, dto.getTasks().size());
        assertEquals("高", dto.getTasks().get(0).getTitle());
    }

    @Test
    void 管理者は日付を指定して部署全体のタスクを絞り込める() {
        LocalDate today = LocalDate.now();
        when(taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(COMPANY_A, DEPT_SALES_A))
                .thenReturn(List.of(
                        taskWithRange(1L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, today, today),
                        taskWithRange(2L, DEPT_SALES_A, 1L, 1L, TaskStatus.UNRESOLVED, today.plusDays(5), today.plusDays(5))));

        AdminTaskListDto dto = service.getForAdmin(COMPANY_A, DEPT_SALES_A, null, null, null, null, today);

        assertEquals(1, dto.getTasks().size());
        assertEquals(today, dto.getDate());
    }

    private Task taskWithRange(Long id, Long departmentId, Long assignedUserId, Long createdByUserId,
                                TaskStatus status, LocalDate startDate, LocalDate dueDate) {
        return Task.builder().id(id).companyId(COMPANY_A).departmentId(departmentId)
                .assignedUserId(assignedUserId).createdByUserId(createdByUserId)
                .title("タスク" + id).status(status).priority(TaskPriority.MEDIUM)
                .startDate(startDate).dueDate(dueDate).build();
    }
}
