package com.kinn.app.repository;

import com.kinn.app.entity.Task;
import com.kinn.app.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * ユーザー別タスク取得(㉚)。「マイタスク」画面の唯一の取得元。
     * assignedUserId(= AppUser.id)はDBの主キーであり会社をまたいで衝突しないため、
     * これ単体で他社・他ユーザーのタスクが紛れ込む心配はない。
     */
    List<Task> findByAssignedUserIdOrderByCreatedAtDesc(Long assignedUserId);

    /** 会社別タスク取得(㉚)。管理者が部署未指定(全部署)で見るときに使う */
    List<Task> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /** 部署別タスク取得(㉚)。company_id + department_id の組み合わせで他社データの混入を防ぐ */
    List<Task> findByCompanyIdAndDepartmentIdOrderByCreatedAtDesc(Long companyId, Long departmentId);

    /**
     * タスク1件の所有権確認用(㉑㊳非常に重要)。idだけでなくcompany_idも一致することを要求するため、
     * 他社のタスクIDを推測して指定してもOptional.emptyになる(URL改ざん対策)。
     */
    Optional<Task> findByIdAndCompanyId(Long id, Long companyId);

    /** ステータス別取得(㉚)。会社全体の集計・絞り込みに使う */
    List<Task> findByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, TaskStatus status);

    /**
     * 期限別取得(㉚)。期限超過タスクの抽出に使う想定。現在の一覧・集計APIは既に取得済みの
     * 一覧から期限判定するため追加クエリを発行しないが、将来「期限超過タスクだけの一覧」
     * のような専用画面を作る際にそのまま使えるよう用意しておく。
     */
    List<Task> findByCompanyIdAndDueDateBeforeAndStatusNot(Long companyId, LocalDate date, TaskStatus excludedStatus);

    /**
     * 締め切りアラート用(本日締め切り・期限切れの未完了タスク)。TaskService#getMyAlerts参照。
     * assignedUserId(=AppUser.id)は会社をまたいで衝突しないため、これ単体で他人・他社の
     * タスクが紛れ込む心配はない(findByAssignedUserIdOrderByCreatedAtDescと同じ考え方)。
     */
    List<Task> findByAssignedUserIdAndStatusNotAndDueDateLessThanEqualOrderByDueDateAsc(
            Long assignedUserId, TaskStatus excludedStatus, LocalDate date);
}
