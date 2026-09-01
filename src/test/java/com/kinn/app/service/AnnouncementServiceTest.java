package com.kinn.app.service;

import com.kinn.app.dto.AnnouncementDto;
import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.UserRole;
import com.kinn.app.repository.AnnouncementAuditLogRepository;
import com.kinn.app.repository.AnnouncementReadRepository;
import com.kinn.app.repository.AnnouncementRepository;
import com.kinn.app.repository.AppUserRepository;
import com.kinn.app.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AnnouncementService の入力チェック(文字数上限。セキュリティレビュー指摘対応)を検証する
 * ユニットテスト。DBは使わず、依存はすべてモック化する(TaskServiceTestと同じ方式)。
 */
class AnnouncementServiceTest {

    private static final Long COMPANY_A = 1L;

    private AnnouncementRepository announcementRepository;
    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        announcementRepository = mock(AnnouncementRepository.class);
        AnnouncementReadRepository announcementReadRepository = mock(AnnouncementReadRepository.class);
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        DepartmentService departmentService = new DepartmentService(departmentRepository, appUserRepository);
        AnnouncementAuditLogRepository announcementAuditLogRepository = mock(AnnouncementAuditLogRepository.class);
        AnnouncementAuditLogService announcementAuditLogService =
                new AnnouncementAuditLogService(announcementAuditLogRepository);

        service = new AnnouncementService(announcementRepository, announcementReadRepository,
                appUserRepository, departmentService, announcementAuditLogService);

        // save()は引数のEntityをそのまま返す(IDENTITY採番のシミュレートまでは行わない、
        // 文字数チェックが通過した後の後続処理が例外なく完走することだけを確認するため)。
        when(announcementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AppUser admin() {
        return AppUser.builder().id(1L).companyId(COMPANY_A).fullName("管理者").role(UserRole.ADMIN).build();
    }

    @Test
    void createByAdmin_titleOver200Characters_isRejected() {
        AnnouncementDto dto = AnnouncementDto.builder()
                .title("あ".repeat(201))
                .body("本文")
                .publishedAt(LocalDateTime.now())
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createByAdmin(admin(), dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("タイトルは200文字以内で入力してください", ex.getReason());
    }

    @Test
    void createByAdmin_titleExactly200Characters_isAccepted() {
        AnnouncementDto dto = AnnouncementDto.builder()
                .title("あ".repeat(200))
                .body("本文")
                .publishedAt(LocalDateTime.now())
                .build();

        AnnouncementDto result = assertDoesNotThrow(() -> service.createByAdmin(admin(), dto));
        assertEquals(200, result.getTitle().length());
    }

    @Test
    void createByAdmin_bodyOver4000Characters_isRejected() {
        AnnouncementDto dto = AnnouncementDto.builder()
                .title("タイトル")
                .body("あ".repeat(4001))
                .publishedAt(LocalDateTime.now())
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createByAdmin(admin(), dto));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("本文は4000文字以内で入力してください", ex.getReason());
    }
}
