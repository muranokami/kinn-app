package com.kinn.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 新規登録画面(既存の会社に参加する場合)が、入力された会社コードから会社名・部署一覧を
 * 表示するための公開API応答。個人情報は一切含まない(部署名の一覧のみ)。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyLookupDto {
    private String companyName;
    private List<String> departmentNames;
}
