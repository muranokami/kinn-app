package com.kinn.app.service;

import com.kinn.app.dto.AdminTaskListDto;
import com.kinn.app.dto.DepartmentDayTaskDto;
import com.kinn.app.dto.MyTaskBoardDto;
import com.kinn.app.dto.TaskAlertDto;
import com.kinn.app.dto.TaskAlertKind;
import com.kinn.app.dto.TaskAlertsDto;
import com.kinn.app.dto.TaskAssigneeOptionDto;
import com.kinn.app.dto.TaskDto;
import com.kinn.app.dto.TaskProgressRowDto;
import com.kinn.app.dto.TaskSummaryDto;
import com.kinn.app.dto.UserTaskGroupDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.Department;
import com.kinn.app.entity.Task;
import com.kinn.app.entity.TaskPriority;
import com.kinn.app.entity.TaskStatus;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * タスク管理機能のサービス。
 *
 * 一般ユーザー向け(㉓「自分のタスク」の閲覧・登録・ステータス変更・編集・削除)と
 * 管理者向け(⑦⑩〜⑯⑲⑳㉒「自社の部署・ユーザーを横断したタスク管理」)の両方をこの1つの
 * サービスにまとめている(DepartmentScheduleServiceと同じ設計方針。共有Entityに対する
 * 「誰が呼ぶかで許可される操作範囲が変わる」ロジックを1箇所に集約するため)。
 *
 * 会社・部署・担当者のアクセス制御(㉑㊳非常に重要)は、すべてのメソッドで以下を徹底する:
 * <ul>
 *   <li>一般ユーザー経路: companyId/departmentIdはリクエストから一切受け取らず、
 *       常にログインユーザー(AppUser)自身の値で上書きする。</li>
 *   <li>管理者経路: companyIdは必ずログイン中の管理者自身の会社。departmentId/assignedUserIdは
 *       リクエストの値を使うが、DepartmentService#requireOwned / AppUserRepository#findByIdAndCompanyId
 *       で「自社に実在するか」を確認し、さらに「指定した部署に指定した担当者が本当に所属しているか」
     *       まで確認してから保存する(㊲「別部署のユーザーを指定」エラーの実装箇所)。</li>
 * </ul>
 *
 * 依頼者・担当者(②)は既存の createdByUserId(依頼者=登録者本人) / assignedUserId(担当者) を
 * そのまま利用する(新規カラムは追加しない)。一般ユーザーが自分で登録するタスクでも、
 * 担当者だけは「同じ部署内であれば自分以外を指定できる」(②の例: 依頼者=山田・担当者=佐藤)。
 * 依頼者(createdByUserId)は常にログインユーザー自身に固定され、なりすましはできない。
 *
 * 部署内共有(⑤⑥⑦): 同じ会社・同じ部署のメンバーであれば、担当者・依頼者を問わずその部署の
 * タスクを閲覧できる({@link #getDetailForUser} / {@link #getDepartmentDay}参照)。
 * 編集・削除の権限は従来どおり担当者本人・依頼者本人・管理者のみ(閲覧できることと
 * 操作できることは別、という原則を崩さない)。
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final AppUserRepository appUserRepository;
    private final DepartmentService departmentService;

    public TaskService(TaskRepository taskRepository,
                        AppUserRepository appUserRepository,
                        DepartmentService departmentService) {
        this.taskRepository = taskRepository;
        this.appUserRepository = appUserRepository;
        this.departmentService = departmentService;
    }

    // ------------------------------------------------------------------
    // 一般ユーザー: マイタスク(③④㉞)
    // ------------------------------------------------------------------

    /** 自分に割り当てられたタスクを未対応/対応中/完了の3列に分けて返す */
    @Transactional(readOnly = true)
    public MyTaskBoardDto getMyBoard(AppUser user) {
        List<Task> tasks = taskRepository.findByAssignedUserIdOrderByCreatedAtDesc(user.getId());
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(user.getCompanyId());
        Map<Long, String> userNames = userNameMapForCompany(user.getCompanyId());

        List<TaskDto> unresolved = new ArrayList<>();
        List<TaskDto> inProgress = new ArrayList<>();
        List<TaskDto> completed = new ArrayList<>();
        for (Task t : tasks) {
            TaskDto dto = toDto(t, deptNames.get(t.getDepartmentId()),
                    userNames.get(t.getAssignedUserId()), userNames.get(t.getCreatedByUserId()));
            switch (t.getStatus()) {
                case UNRESOLVED -> unresolved.add(dto);
                case IN_PROGRESS -> inProgress.add(dto);
                case COMPLETED -> completed.add(dto);
            }
        }
        return MyTaskBoardDto.builder()
                .unresolved(unresolved)
                .inProgress(inProgress)
                .completed(completed)
                .summary(summarize(tasks))
                .build();
    }

    /**
     * 締め切りアラート(本日締め切り・期限切れの、ログインユーザー本人が担当するタスクのみ)。
     * getMyBoard()とは別の軽量な専用メソッドとして追加している(既存メソッドの戻り値・挙動は
     * 変更しない)。「進捗管理の一般的な注意喚起」という位置づけのため、文言もkindLabelを
     * そのまま使えばやわらかい表現になるようTaskAlertKindの方に持たせている。
     */
    @Transactional(readOnly = true)
    public TaskAlertsDto getMyAlerts(AppUser user) {
        LocalDate today = LocalDate.now();
        List<Task> candidates = taskRepository
                .findByAssignedUserIdAndStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(
                        user.getId(), TaskStatus.COMPLETED, today);

        List<TaskAlertDto> items = new ArrayList<>();
        int dueTodayCount = 0;
        int overdueCount = 0;
        for (Task t : candidates) {
            TaskAlertKind kind = t.getDueDate().isEqual(today) ? TaskAlertKind.DUE_TODAY : TaskAlertKind.OVERDUE;
            if (kind == TaskAlertKind.DUE_TODAY) dueTodayCount++; else overdueCount++;
            items.add(TaskAlertDto.builder()
                    .id(t.getId())
                    .title(t.getTitle())
                    .dueDate(t.getDueDate())
                    .priority(t.getPriority())
                    .priorityLabel(t.getPriority().getLabel())
                    .kind(kind)
                    .kindLabel(kind.getLabel())
                    .build());
        }
        return TaskAlertsDto.builder()
                .dueTodayCount(dueTodayCount)
                .overdueCount(overdueCount)
                .items(items)
                .build();
    }

    /**
     * 一般ユーザーが自分を依頼者としてタスクを登録する(⑨⑮②)。依頼者(createdByUserId)は
     * 常に本人固定。担当者(assignedUserId)は省略時は本人になるが、dto.getAssignedUserId()で
     * 同じ部署内の別のユーザーを指定することもできる(②「依頼者:山田、担当者:佐藤」の例)。
     * 部署外のユーザーは指定できない(㉔)。
     */
    @Transactional
    public TaskDto createForSelf(AppUser user, TaskDto dto) {
        if (user.getDepartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "部署に所属していないため、タスクを登録できません。管理者に部署設定を依頼してください。");
        }
        validateCommon(dto);
        AppUser assignee = resolveAssigneeInOwnDepartment(user, dto.getAssignedUserId());

        Task entity = Task.builder()
                .companyId(user.getCompanyId())
                .departmentId(user.getDepartmentId())
                .assignedUserId(assignee.getId())
                .createdByUserId(user.getId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(TaskStatus.UNRESOLVED)
                .priority(dto.getPriority() == null ? TaskPriority.MEDIUM : dto.getPriority())
                .startDate(dto.getStartDate())
                .dueDate(dto.getDueDate())
                .notes(dto.getNotes())
                .build();
        Task saved = taskRepository.save(entity);
        String departmentName = departmentService.getDepartmentNameOrNull(saved.getDepartmentId());
        return toDto(saved, departmentName, assignee.getFullName(), user.getFullName());
    }

    /**
     * 一般ユーザーが自分のタスクを編集する(⑱)。所属情報(会社・部署・担当者)は変更できない
     * (このメソッドはdto.getDepartmentId()/dto.getAssignedUserId()を一切読まないため、
     * 送信されても無視される=改ざんの余地がない)。
     */
    @Transactional
    public TaskDto updateOwn(AppUser user, Long taskId, TaskDto dto) {
        Task entity = findOwnedByAssigneeOrCreator(user, taskId, "編集");
        validateCommon(dto);

        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        entity.setStartDate(dto.getStartDate());
        entity.setDueDate(dto.getDueDate());
        entity.setNotes(dto.getNotes());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());

        Task saved = taskRepository.save(entity);
        return toDtoResolved(saved);
    }

    /** ステータスのみを変更する(⑤「対応開始」「完了」ボタン・プルダウン共通) */
    @Transactional
    public TaskDto updateStatusOwn(AppUser user, Long taskId, TaskStatus status) {
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ステータスは必須です");
        }
        Task entity = findOwnedByAssignee(user, taskId, "ステータス変更");
        entity.setStatus(status);
        Task saved = taskRepository.save(entity);
        return toDtoResolved(saved);
    }

    /** 自分で作成したタスクを削除する(⑳。担当を割り当てられただけのタスクは削除できない) */
    @Transactional
    public void deleteOwn(AppUser user, Long taskId) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "このタスクは見つかりません。既に削除されている可能性があります。"));
        if (!entity.getCreatedByUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "このタスクを削除する権限がありません。自分で作成したタスクのみ削除できます。");
        }
        taskRepository.delete(entity);
    }

    /**
     * タスク詳細(⑰)。担当者本人・依頼者本人・自社の管理者に加えて、⑤⑥⑦により
     * 「同じ会社・同じ部署のメンバー」は閲覧できる(編集・削除は別権限。findOwnedByAssigneeOrCreator
     * /deleteOwnで別途チェックするため、ここで閲覧を許可しても操作できるわけではない)。
     */
    @Transactional(readOnly = true)
    public TaskDto getDetailForUser(AppUser user, Long taskId) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "このタスクは見つかりません。"));
        boolean isOwner = entity.getAssignedUserId().equals(user.getId())
                || entity.getCreatedByUserId().equals(user.getId());
        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        boolean isSameDepartment = user.getDepartmentId() != null
                && user.getDepartmentId().equals(entity.getDepartmentId());
        if (!isOwner && !isAdmin && !isSameDepartment) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このタスクを表示する権限がありません。");
        }
        return toDtoResolved(entity);
    }

    /**
     * 部署別タスク一覧・日別表示(①②③④⑤⑥⑦⑧⑨⑩⑪⑬⑭⑮)。ログインユーザー自身の会社・部署から
     * 自動的に解決するため(departmentIdをリクエストから受け取らない)、他部署・他社の
     * タスクが混ざる余地はない(㉔)。部署未所属ユーザーは空の結果を返す。
     *
     * @param date 指定時はその日に該当するタスクのみに絞り込む(⑩⑪日別表示)。
     *             nullの場合は日付で絞り込まない=部署の全タスクを表示する(①部署別タスク一覧)。
     * @param assignedUserFilter 指定時はその担当者のタスクのみに絞り込む(⑦ユーザー別フィルター)
     * @param statusFilter 指定時はそのステータスのタスクのみに絞り込む(⑧ステータス別フィルター)
     */
    @Transactional(readOnly = true)
    public DepartmentDayTaskDto getDepartmentDay(AppUser user, LocalDate date, Long assignedUserFilter,
                                                  TaskStatus statusFilter) {
        if (user.getDepartmentId() == null) {
            return DepartmentDayTaskDto.builder()
                    .date(date).departmentId(null).departmentName(null)
                    .summary(summarize(List.of()))
                    .unresolved(List.of()).inProgress(List.of()).completed(List.of())
                    .byUser(List.of())
                    .build();
        }
        return buildDepartmentDayView(user.getCompanyId(), user.getDepartmentId(), date, assignedUserFilter, statusFilter);
    }

    /** タスク登録フォームの「担当者」選択肢(②)。ログインユーザー自身の部署のメンバーのみ返す */
    @Transactional(readOnly = true)
    public List<TaskAssigneeOptionDto> getDepartmentMembers(AppUser user) {
        if (user.getDepartmentId() == null) {
            return List.of();
        }
        return appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(user.getCompanyId(), user.getDepartmentId())
                .stream()
                .map(u -> TaskAssigneeOptionDto.builder().userId(u.getId()).fullName(u.getFullName()).build())
                .toList();
    }

    // ------------------------------------------------------------------
    // 管理者(⑦⑩〜⑯⑲⑳㉖㉗㉘)
    // ------------------------------------------------------------------

    /**
     * 部署・担当者・依頼者・ステータス・優先度・日付で絞り込んだタスク一覧+集計
     * (⑩⑫⑬⑭⑮⑯⑰⑱⑲⑳㉖)。departmentIdがnullなら全部署(会社全体)。他の引数もnullなら
     * その条件では絞り込まない。dateを指定すると「開始日&lt;=date&lt;=期限」のタスクのみに
     * 絞り込む(⑨⑪と同じ判定ロジック。{@link #isOnDate}参照)ため、⑰⑱の「部署+日付」画面と
     * ⑲の「依頼者別」画面、㉑の「依頼者・担当者・部署・日付・ステータス・優先度」検索を
     * このメソッド1つで兼ねる。
     */
    @Transactional(readOnly = true)
    public AdminTaskListDto getForAdmin(Long companyId, Long departmentId, Long assignedUserId,
                                         Long requesterUserId, TaskStatus status, TaskPriority priority, LocalDate date) {
        List<Task> scoped = loadScope(companyId, departmentId);
        List<Task> filtered = scoped.stream()
                .filter(t -> assignedUserId == null || assignedUserId.equals(t.getAssignedUserId()))
                .filter(t -> requesterUserId == null || requesterUserId.equals(t.getCreatedByUserId()))
                .filter(t -> status == null || status == t.getStatus())
                .filter(t -> priority == null || priority == t.getPriority())
                .filter(t -> date == null || isOnDate(t, date))
                .toList();

        String departmentName = departmentId == null ? null
                : departmentService.getDepartmentNameOrNull(departmentId);
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(companyId);
        Map<Long, String> userNames = userNameMapForCompany(companyId);

        List<TaskDto> rows = filtered.stream()
                .map(t -> toDto(t, deptNames.get(t.getDepartmentId()),
                        userNames.get(t.getAssignedUserId()), userNames.get(t.getCreatedByUserId())))
                .toList();

        return AdminTaskListDto.builder()
                .departmentId(departmentId)
                .departmentName(departmentName)
                .date(date)
                .summary(summarize(filtered))
                .tasks(rows)
                .build();
    }

    /** ユーザー別進捗(㉗)。departmentId指定時はその部署所属ユーザーのみ、nullなら全社員 */
    @Transactional(readOnly = true)
    public List<TaskProgressRowDto> getUserProgress(Long companyId, Long departmentId) {
        List<AppUser> users = departmentId == null
                ? appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId)
                : appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(
                        companyId, departmentService.requireOwned(companyId, departmentId).getId());

        List<Task> tasks = loadScope(companyId, departmentId);
        Map<Long, List<Task>> byUser = tasks.stream().collect(Collectors.groupingBy(Task::getAssignedUserId));

        List<TaskProgressRowDto> rows = new ArrayList<>();
        for (AppUser u : users) {
            rows.add(progressRow(u.getId(), u.getFullName(), byUser.getOrDefault(u.getId(), List.of())));
        }
        return rows;
    }

    /** 部署別進捗(㉘)。自社の全部署を対象に、部署ごとのタスク件数をまとめる */
    @Transactional(readOnly = true)
    public List<TaskProgressRowDto> getDepartmentProgress(Long companyId) {
        List<Task> tasks = taskRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<Long, List<Task>> byDept = tasks.stream().collect(Collectors.groupingBy(Task::getDepartmentId));
        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(companyId);

        List<TaskProgressRowDto> rows = new ArrayList<>();
        for (Map.Entry<Long, String> e : deptNames.entrySet()) {
            rows.add(progressRow(e.getKey(), e.getValue(), byDept.getOrDefault(e.getKey(), List.of())));
        }
        return rows;
    }

    /** 管理者がタスクを新規登録する(⑦)。部署・担当者は必ず自社かつ整合していることを確認する(㊲) */
    @Transactional
    public TaskDto createByAdmin(Long companyId, Long createdByUserId, TaskDto dto) {
        if (dto.getDepartmentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "担当部署は必須です");
        }
        if (dto.getAssignedUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "担当ユーザーは必須です");
        }
        Department department = departmentService.requireOwned(companyId, dto.getDepartmentId());
        AppUser assignee = requireOwnedUserInDepartment(companyId, department, dto.getAssignedUserId());
        validateCommon(dto);

        Task entity = Task.builder()
                .companyId(companyId)
                .departmentId(department.getId())
                .assignedUserId(assignee.getId())
                .createdByUserId(createdByUserId)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus() == null ? TaskStatus.UNRESOLVED : dto.getStatus())
                .priority(dto.getPriority() == null ? TaskPriority.MEDIUM : dto.getPriority())
                .startDate(dto.getStartDate())
                .dueDate(dto.getDueDate())
                .notes(dto.getNotes())
                .build();
        Task saved = taskRepository.save(entity);
        return toDto(saved, department.getName(), assignee.getFullName(), resolveUserName(createdByUserId));
    }

    /** 管理者がタスクを編集する(⑲)。自社のタスクのみ対象(他社のタスクIDは404) */
    @Transactional
    public TaskDto updateByAdmin(Long companyId, Long taskId, TaskDto dto) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のタスクが見つかりません"));

        Long targetDepartmentId = dto.getDepartmentId() != null ? dto.getDepartmentId() : entity.getDepartmentId();
        Long targetAssignedUserId = dto.getAssignedUserId() != null ? dto.getAssignedUserId() : entity.getAssignedUserId();
        Department department = departmentService.requireOwned(companyId, targetDepartmentId);
        AppUser assignee = requireOwnedUserInDepartment(companyId, department, targetAssignedUserId);
        validateCommon(dto);

        entity.setDepartmentId(department.getId());
        entity.setAssignedUserId(assignee.getId());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        entity.setStartDate(dto.getStartDate());
        entity.setDueDate(dto.getDueDate());
        entity.setNotes(dto.getNotes());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());

        Task saved = taskRepository.save(entity);
        return toDto(saved, department.getName(), assignee.getFullName(), resolveUserName(saved.getCreatedByUserId()));
    }

    /** 管理者がタスクを削除する(⑳)。自社のタスクのみ対象 */
    @Transactional
    public void deleteByAdmin(Long companyId, Long taskId) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定のタスクが見つかりません"));
        taskRepository.delete(entity);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /** departmentId指定時は「本当に自社の部署か」を確認したうえで絞り込む(⑨⑩⑫。㊳会社・部署アクセス制御) */
    private List<Task> loadScope(Long companyId, Long departmentId) {
        if (departmentId == null) {
            return taskRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        }
        departmentService.requireOwned(companyId, departmentId);
        return taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(companyId, departmentId);
    }

    /**
     * 指定した部署に、指定したユーザーが本当に所属しているかを確認する(⑦㊲「別部署のユーザーを指定」
     * エラーの実装箇所)。ユーザー自体は既にDepartmentService#requireOwnedで確認済みのcompanyId配下から
     * 探すため、他社ユーザーの指定は「担当ユーザーが存在しない」(㊲)として弾かれる。
     */
    private AppUser requireOwnedUserInDepartment(Long companyId, Department department, Long userId) {
        AppUser user = appUserRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定の担当ユーザーが見つかりません"));
        if (!department.getId().equals(user.getDepartmentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "指定した部署(" + department.getName() + ")に所属していない担当者です");
        }
        return user;
    }

    /**
     * 担当者の解決(②)。指定が無い、または自分自身を指定した場合はそのまま本人。
     * それ以外を指定した場合は「本当に自分と同じ部署のユーザーか」を確認する(㉔。
     * 別部署・別会社のユーザーへ勝手に依頼できてしまうことを防ぐ)。
     */
    private AppUser resolveAssigneeInOwnDepartment(AppUser requester, Long requestedAssignedUserId) {
        if (requestedAssignedUserId == null || requestedAssignedUserId.equals(requester.getId())) {
            return requester;
        }
        AppUser assignee = appUserRepository.findByIdAndCompanyId(requestedAssignedUserId, requester.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定した担当ユーザーが見つかりません"));
        if (!requester.getDepartmentId().equals(assignee.getDepartmentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ部署のユーザーにのみ依頼できます");
        }
        return assignee;
    }

    /**
     * 部署別タスク一覧・日別表示の本体(①②③④⑤⑥⑦⑧⑨⑩⑪⑬⑭⑮)。ステータス別(⑬)・
     * 担当者別(⑭)の両方の形で組み立てる。日付・担当者・ステータスの各フィルターは
     * どちらの表示形式にも同じ絞り込み結果が反映されるよう、最初に1回だけ適用する。
     * dateがnullの場合は日付で絞り込まず、部署の全タスクを対象にする(①)。
     */
    private DepartmentDayTaskDto buildDepartmentDayView(Long companyId, Long departmentId, LocalDate date,
                                                          Long assignedUserFilter, TaskStatus statusFilter) {
        String departmentName = departmentService.getDepartmentNameOrNull(departmentId);
        List<Task> deptTasks = taskRepository.findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(companyId, departmentId);
        List<Task> dayTasks = deptTasks.stream()
                .filter(t -> date == null || isOnDate(t, date))
                .filter(t -> assignedUserFilter == null || assignedUserFilter.equals(t.getAssignedUserId()))
                .filter(t -> statusFilter == null || statusFilter == t.getStatus())
                .toList();

        Map<Long, String> deptNames = departmentService.getDepartmentNameMap(companyId);
        Map<Long, String> userNames = userNameMapForCompany(companyId);

        List<TaskDto> unresolved = new ArrayList<>();
        List<TaskDto> inProgress = new ArrayList<>();
        List<TaskDto> completed = new ArrayList<>();
        for (Task t : dayTasks) {
            TaskDto dto = toDto(t, deptNames.get(t.getDepartmentId()),
                    userNames.get(t.getAssignedUserId()), userNames.get(t.getCreatedByUserId()));
            switch (t.getStatus()) {
                case UNRESOLVED -> unresolved.add(dto);
                case IN_PROGRESS -> inProgress.add(dto);
                case COMPLETED -> completed.add(dto);
            }
        }

        Map<Long, List<Task>> byUserMap = dayTasks.stream().collect(Collectors.groupingBy(Task::getAssignedUserId));
        List<AppUser> deptUsers = appUserRepository.findByCompanyIdAndDepartmentIdOrderByFullNameAsc(companyId, departmentId);
        List<UserTaskGroupDto> byUser = new ArrayList<>();
        for (AppUser u : deptUsers) {
            List<TaskDto> tasksForUser = byUserMap.getOrDefault(u.getId(), List.of()).stream()
                    .map(t -> toDto(t, deptNames.get(t.getDepartmentId()),
                            userNames.get(t.getAssignedUserId()), userNames.get(t.getCreatedByUserId())))
                    .toList();
            byUser.add(UserTaskGroupDto.builder().userId(u.getId()).userName(u.getFullName()).tasks(tasksForUser).build());
        }

        return DepartmentDayTaskDto.builder()
                .date(date).departmentId(departmentId).departmentName(departmentName)
                .summary(summarize(dayTasks))
                .unresolved(unresolved).inProgress(inProgress).completed(completed)
                .byUser(byUser)
                .build();
    }

    /**
     * タスクが指定日に「該当する」かどうか(⑨⑪)。開始日&lt;=date&lt;=期限を基本とし、
     * 片方しか設定されていない場合はもう片方をそれと同じ日として扱う(⑫「1日タスク」=
     * 開始日と期限が同じ日、という登録方法とも自然に整合する)。両方とも未設定のタスクは
     * 特定の日に紐付けられないため、日別表示の対象外とする(通常の一覧・マイタスクには
     * 引き続き表示される。日別表示だけの都合)。
     */
    private boolean isOnDate(Task t, LocalDate date) {
        LocalDate start = t.getStartDate() != null ? t.getStartDate() : t.getDueDate();
        LocalDate end = t.getDueDate() != null ? t.getDueDate() : t.getStartDate();
        if (start == null || end == null) return false;
        if (end.isBefore(start)) return false; // 通常はvalidateCommonで弾かれるが念のため
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** 所有者チェック(⑱非常に重要): 担当者本人 または 登録者本人のみ編集できる */
    private Task findOwnedByAssigneeOrCreator(AppUser user, Long taskId, String action) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "このタスクは見つかりません。既に削除されている可能性があります。"));
        boolean isAssignee = entity.getAssignedUserId().equals(user.getId());
        boolean isCreator = entity.getCreatedByUserId().equals(user.getId());
        if (!isAssignee && !isCreator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "このタスクを" + action + "する権限がありません。");
        }
        return entity;
    }

    /** ステータス変更は「担当者本人」のみ(⑤)。登録しただけの管理者・他ユーザーは不可 */
    private Task findOwnedByAssignee(AppUser user, Long taskId, String action) {
        Task entity = taskRepository.findByIdAndCompanyId(taskId, user.getCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "このタスクは見つかりません。既に削除されている可能性があります。"));
        if (!entity.getAssignedUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "このタスクを" + action + "する権限がありません。自分が担当するタスクのみ操作できます。");
        }
        return entity;
    }

    /** 入力チェック(㊲)。タスク名は必須、期限は開始日より前にできない */
    private void validateCommon(TaskDto dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "タスク名は必須です");
        }
        if (dto.getStartDate() != null && dto.getDueDate() != null && dto.getDueDate().isBefore(dto.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "期限は開始日より前にできません");
        }
    }

    private TaskSummaryDto summarize(List<Task> tasks) {
        int unresolved = 0, inProgress = 0, completed = 0, overdue = 0;
        LocalDate today = LocalDate.now();
        for (Task t : tasks) {
            switch (t.getStatus()) {
                case UNRESOLVED -> unresolved++;
                case IN_PROGRESS -> inProgress++;
                case COMPLETED -> completed++;
            }
            if (isOverdue(t, today)) overdue++;
        }
        return TaskSummaryDto.builder()
                .totalCount(tasks.size())
                .unresolvedCount(unresolved)
                .inProgressCount(inProgress)
                .completedCount(completed)
                .overdueCount(overdue)
                .build();
    }

    private TaskProgressRowDto progressRow(Long id, String name, List<Task> tasks) {
        int unresolved = 0, inProgress = 0, completed = 0;
        for (Task t : tasks) {
            switch (t.getStatus()) {
                case UNRESOLVED -> unresolved++;
                case IN_PROGRESS -> inProgress++;
                case COMPLETED -> completed++;
            }
        }
        return TaskProgressRowDto.builder()
                .id(id).name(name)
                .unresolvedCount(unresolved).inProgressCount(inProgress).completedCount(completed)
                .build();
    }

    private boolean isOverdue(Task t, LocalDate today) {
        return t.getDueDate() != null && t.getStatus() != TaskStatus.COMPLETED && t.getDueDate().isBefore(today);
    }

    private TaskDto toDtoResolved(Task t) {
        String deptName = departmentService.getDepartmentNameOrNull(t.getDepartmentId());
        return toDto(t, deptName, resolveUserName(t.getAssignedUserId()), resolveUserName(t.getCreatedByUserId()));
    }

    /**
     * ②担当者は必ず氏名で表示する。何らかの理由(担当者だったユーザーが後から削除された等)で
     * 氏名が引けない場合も、null や userId をそのまま返さず「未割り当て」を返す
     * (assignedUserIdはTask entityでNOT NULL制約のため通常は必ず設定されているが、
     * 担当者アカウントの削除は別画面[管理者の従業員削除]から独立して行えるため、
     * 参照先が失われるケースを防御的に考慮する)。
     */
    private String assignedNameOrFallback(String assignedUserName) {
        return assignedUserName != null ? assignedUserName : "未割り当て";
    }

    private TaskDto toDto(Task t, String departmentName, String assignedUserName, String createdByName) {
        return TaskDto.builder()
                .id(t.getId())
                .departmentId(t.getDepartmentId())
                .departmentName(departmentName)
                .assignedUserId(t.getAssignedUserId())
                .assignedUserName(assignedNameOrFallback(assignedUserName))
                .createdByUserId(t.getCreatedByUserId())
                .createdByName(createdByName)
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .statusLabel(t.getStatus().getLabel())
                .priority(t.getPriority())
                .priorityLabel(t.getPriority().getLabel())
                .startDate(t.getStartDate())
                .dueDate(t.getDueDate())
                .notes(t.getNotes())
                .overdue(isOverdue(t, LocalDate.now()))
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    /** N+1回避用: 会社内の全ユーザーのID→氏名をまとめて1回で引く(Map.of()はnullキー不可のためHashMapを使う) */
    private Map<Long, String> userNameMapForCompany(Long companyId) {
        Map<Long, String> map = new HashMap<>();
        for (AppUser u : appUserRepository.findByCompanyIdOrderByFullNameAsc(companyId)) {
            map.put(u.getId(), u.getFullName());
        }
        return map;
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return appUserRepository.findById(userId).map(AppUser::getFullName).orElse(null);
    }
}
