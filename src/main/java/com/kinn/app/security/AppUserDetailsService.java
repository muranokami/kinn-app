package com.kinn.app.security;

import com.kinn.app.entity.AppUser;
import com.kinn.app.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring SecurityのUserDetailsService実装。
 * 引数の username は "companyId|loginId" 形式(= AppUserPrincipal#getUsername と同じ形式)を
 * 前提とする。この文字列はログイン画面が送ってくる会社名・ユーザーIDから
 * AuthController が組み立てて渡す(会社名の名前解決はここではなくAuthController側で行う。
 * UserDetailsServiceの契約上、渡された username だけで一意にユーザーを特定する必要があるため)。
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        long companyId;
        String loginId;
        try {
            int sep = username.indexOf('|');
            if (sep < 0) throw new IllegalArgumentException();
            companyId = Long.parseLong(username.substring(0, sep));
            loginId = username.substring(sep + 1);
        } catch (Exception e) {
            throw new UsernameNotFoundException("ユーザーが見つかりません");
        }

        AppUser user = appUserRepository.findByCompanyIdAndLoginId(companyId, loginId)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));
        return new AppUserPrincipal(user);
    }
}
