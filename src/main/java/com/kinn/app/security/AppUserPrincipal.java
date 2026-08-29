package com.kinn.app.security;

import com.kinn.app.entity.AppUser;
import com.kinn.app.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Spring SecurityのUserDetails実装。
 * getUsername()が返す値(= 実効employeeId = "companyId|loginId")が、既存の全機能
 * (勤怠・健康・食事・レシピなど)がこれまで employeeId として使っていた値と同じ形式になる。
 * これにより Authentication#getName() を呼ぶだけで、既存テーブルのスコープ用IDがそのまま手に入る。
 */
public class AppUserPrincipal implements UserDetails {

    private final AppUser user;

    public AppUserPrincipal(AppUser user) {
        this.user = user;
    }

    public AppUser getAppUser() {
        return user;
    }

    public String getFullName() {
        return user.getFullName();
    }

    public UserRole getRole() {
        return user.getRole();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** = 実効employeeId("companyId|loginId")。既存機能全体がこれをそのままemployeeIdとして使う */
    @Override
    public String getUsername() {
        return user.effectiveEmployeeId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** ログイン失敗回数の超過による一時ロック中はfalse(DaoAuthenticationProviderが自動的にチェックする) */
    @Override
    public boolean isAccountNonLocked() {
        LocalDateTime lockedUntil = user.getLockedUntil();
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 管理者が無効化した(退職者等の)アカウントはfalse。
     * nullは有効(true)として扱う(既存行の列追加時・想定外の状態でログイン不能に
     * ならないようにするフェイルセーフ。AppUser側のDB defaultとあわせた二重の安全策)。
     */
    @Override
    public boolean isEnabled() {
        return user.getEnabled() == null || user.getEnabled();
    }

    /**
     * maximumSessions(同時ログインセッション数の制限)はSpring SecurityのSessionRegistryが
     * 「同じprincipal」をequals()で突き合わせて数えるため、これを実装しないと
     * ログインの都度new AppUserPrincipal(user)される本実装では常に別ユーザー扱いになり
     * 制限が機能しない。実効employeeId("companyId|loginId")が同じなら同一人物とみなす。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppUserPrincipal other)) return false;
        return getUsername().equals(other.getUsername());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsername());
    }
}
