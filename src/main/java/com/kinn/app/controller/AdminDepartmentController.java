package com.kinn.app.controller;

import com.kinn.app.dto.DepartmentCreateRequestDto;
import com.kinn.app.dto.DepartmentDto;
import com.kinn.app.security.AppUserPrincipal;
import com.kinn.app.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理者向け部署管理API。ログイン中の管理者と同じ会社の部署のみを対象とする。
 */
@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentDto> getDepartments(@AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentService.getDepartments(principal.getAppUser().getCompanyId());
    }

    @PostMapping
    public DepartmentDto createDepartment(
            @Valid @RequestBody DepartmentCreateRequestDto dto,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return departmentService.createDepartment(principal.getAppUser().getCompanyId(), dto);
    }
}
