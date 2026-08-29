package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDetailDto {
    private Long userId;
    private String loginId;
    private String fullName;
    private String companyName;
    private Long departmentId;
    private String departmentName;
    private String position;
    /** パスワードを忘れた際のセルフサービスリセットに使うメールアドレス(未登録ならnull) */
    private String email;
    private String role;
    private LocalDateTime createdAt;
    /** 退職者アカウント等を無効化しているとfalse(ログイン不可) */
    private boolean enabled;
}
