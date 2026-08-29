package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 従業員の所属部署変更リクエスト。departmentId=null は「未所属」に戻すことを表す */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDepartmentUpdateRequestDto {
    private Long departmentId;
}
