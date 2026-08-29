package com.kinn.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 部署。会社(companyId)に必ず紐付く(会社Aの「営業部」と会社Bの「営業部」は別データ)。
 *
 * Company → Department → AppUser という階層を、既存Entityの設計方針(@ManyToOne等の
 * 関連は張らずFKカラムのみで参照する)に合わせて表現している。
 */
@Entity
@Table(name = "department", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
