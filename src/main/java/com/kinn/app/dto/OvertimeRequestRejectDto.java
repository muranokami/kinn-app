package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 却下API(PUT /api/admin/overtime-requests/{id}/reject)専用のリクエストボディ。理由は必須 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequestRejectDto {
    private String rejectReason;
}
