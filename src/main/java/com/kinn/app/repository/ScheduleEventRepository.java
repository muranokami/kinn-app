package com.kinn.app.repository;

import com.kinn.app.entity.ScheduleEvent;
import com.kinn.app.entity.ScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, Long> {

    List<ScheduleEvent> findByEmployeeIdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            String employeeId, LocalDate from, LocalDate to);

    /**
     * 部署全体のスケジュール表示用(管理者)。対象社員のemployeeId一覧+期間で1回のクエリに
     * まとめて絞り込むことで、社員数だけクエリを繰り返さないようにする(パフォーマンス対策)。
     */
    List<ScheduleEvent> findByEmployeeIdInAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            Collection<String> employeeIds, LocalDate from, LocalDate to);

    /**
     * 部署共有スケジュールの一覧取得(⑥⑱)。company_id + department_id + schedule_type の
     * 3つを同時に条件にすることで、URLの部署IDを書き換えただけでは他社・他部署のデータに
     * 到達できないようにする(⑨⑩⑪)。一般ユーザー(自分の部署を自動解決)・管理者
     * (明示指定した部署。事前にDepartmentService#requireOwnedで自社所属を確認済み)の
     * どちらの経路でも、最終的に必ずこのメソッドを通す。
     */
    List<ScheduleEvent> findByCompanyIdAndDepartmentIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            Long companyId, Long departmentId, ScheduleType scheduleType, LocalDate from, LocalDate to);

    /**
     * 部署共有スケジュール1件の編集・削除時の所有権確認用(⑯⑰)。idだけでなく
     * company_id/department_id/schedule_type も一致することを要求するため、他社・他部署の
     * イベントIDを推測して指定しても404相当(Optional.empty)になる。
     */
    Optional<ScheduleEvent> findByIdAndCompanyIdAndDepartmentIdAndScheduleType(
            Long id, Long companyId, Long departmentId, ScheduleType scheduleType);

    /**
     * 一般ユーザー自身による部署共有スケジュールの編集・削除用(⑭⑮⑯⑱)。
     * departmentIdでは絞らない: 登録者が後で部署異動になっても、自分が登録した過去の
     * 共有予定は引き続き編集・削除できるようにするため(所有者=登録者はcreatedByUserIdで
     * 別途判定する。ここではcompanyIdのみで他社データへの到達を防ぐ)。
     */
    Optional<ScheduleEvent> findByIdAndCompanyIdAndScheduleType(
            Long id, Long companyId, ScheduleType scheduleType);

    /**
     * 管理者が自社の部署共有スケジュールを「全部署」横断でまとめて閲覧するためのクエリ
     * (一般ユーザーが登録した内容を管理者が全て確認できるようにする)。departmentIdでは
     * 絞らず、company_id + schedule_type のみで条件付けする。他社データには到達しない
     * (company_idを必須条件にしているため)。
     */
    List<ScheduleEvent> findByCompanyIdAndScheduleTypeAndEventDateBetweenOrderByEventDateAscStartTimeAsc(
            Long companyId, ScheduleType scheduleType, LocalDate from, LocalDate to);
}
