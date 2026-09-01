package com.kinn.app.service;

import com.kinn.app.dto.OvertimeRequestDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.OvertimeRequest;
import com.kinn.app.entity.OvertimeRequestStatus;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import com.kinn.app.repository.OvertimeRequestAuditLogRepository;
import com.kinn.app.repository.OvertimeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OvertimeRequestService の入力チェック(文字数上限。セキュリティレビュー指摘対応)を検証する
 * ユニットテスト。DBは使わず、依存はすべてモック化する(TaskServiceTestと同じ方式)。
 */
class OvertimeRequestServiceTest {

    private static final Long COMPANY_A = 1L;

    private OvertimeRequestRepository overtimeRequestRepository;
    private OvertimeRequestService service;

    @BeforeEach
    void setUp() {
        overtimeRequestRepository = mock(OvertimeRequestRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        DepartmentService departmentService = new DepartmentService(departmentRepository, appUserRepository);
        OvertimeRequestAuditLogRepository auditLogRepository = mock(OvertimeRequestAuditLogRepository.class);
        OvertimeRequestAuditLogService auditLogService = new OvertimeRequestAuditLogService(auditLogRepository);

        service = new OvertimeRequestService(overtimeRequestRepository, appUserRepository,
                departmentService, auditLogService);

        when(overtimeRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AppUser applicant() {
        return AppUser.builder().id(1L).companyId(COMPANY_A).fullName("申請者").role(UserRole.USER).build();
    }

    private OvertimeRequestDto dto(String reason) {
        return OvertimeRequestDto.builder()
                .targetDate(LocalDate.now().plusDays(1))
                .plannedMinutes(60)
                .reason(reason)
                .build();
    }

    @Test
    void create_reasonOver500Characters_isRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(applicant(), dto("あ".repeat(501))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("理由は500文字以内で入力してください", ex.getReason());
    }

    @Test
    void create_reasonExactly500Characters_isAccepted() {
        OvertimeRequestDto result = assertDoesNotThrow(() -> service.create(applicant(), dto("あ".repeat(500))));
        assertEquals(500, result.getReason().length());
    }

    @Test
    void reject_reasonOver500Characters_isRejected() {
        OvertimeRequest pending = OvertimeRequest.builder()
                .id(1L).companyId(COMPANY_A).applicantUserId(1L)
                .targetDate(LocalDate.now().plusDays(1)).plannedMinutes(60).reason("残業理由")
                .status(OvertimeRequestStatus.PENDING)
                .build();
        when(overtimeRequestRepository.findByIdAndCompanyId(1L, COMPANY_A)).thenReturn(Optional.of(pending));

        AppUser admin = AppUser.builder().id(2L).companyId(COMPANY_A).fullName("管理者").role(UserRole.ADMIN).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reject(admin, 1L, "あ".repeat(501)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("却下理由は500文字以内で入力してください", ex.getReason());
    }
}
