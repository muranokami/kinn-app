package com.kinn.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理者向けAPI保護の一貫性を機械的に検証するアーキテクチャテスト
 * (2026-08-30セキュリティレビュー対応)。
 *
 * SecurityConfigの認可は「/api/admin/** → hasRole("ADMIN")」というURLパターン一致に
 * 依存している。これ自体は正しく機能するが、新しい管理者用Controllerを追加する際に
 * @RequestMapping を /api/admin/ 始まりにし忘れる・typoするといった実装ミスがあった場合、
 * SecurityConfig側の変更なしに認可が漏れてしまう(URLパターンが一致しなければ
 * anyRequest().authenticated() 側、つまり一般ユーザーでも到達可能な扱いになる)。
 *
 * このテストは、com.kinn.app.controller パッケージ内の全@RestControllerを走査し、
 * 「/api/admin/** にマッピングされているControllerには、クラスレベルの
 * @PreAuthorize("hasRole('ADMIN')") 相当が必ず付いている」「逆に、そのようなアノテーションが
 * 付いているControllerは必ず/api/admin/**にマッピングされている」の両方向を検証する。
 * 前者が崩れるとURLパターンだけに頼った認可漏れを、後者が崩れるとコピペミス等による
 * 意図しない管理者専用化(一般ユーザーが使うべきAPIを誤ってADMIN限定にしてしまう)を検知できる。
 */
class AdminControllerSecurityArchitectureTest {

    static {
        // 外付けドライブ(exFAT)上でmacOSが自動生成するリソースフォーク副ファイル
        // (._AdminAnnouncementController.class 等。.gitignoreの `._*` コメント参照)が
        // target/classes に混在していると、Springのクラスパススキャンがそれをクラスファイルとして
        // 読もうとして失敗する。アプリ本体の起動時(spring-boot:run)は
        // -Dspring.classformat.ignore=true を付けて回避しているのと同じ設定をテストにも適用する。
        System.setProperty("spring.classformat.ignore", "true");
    }

    private static final String CONTROLLER_BASE_PACKAGE = "com.kinn.app.controller";
    private static final String ADMIN_PATH_PREFIX = "/api/admin";

    @Test
    void adminPathControllersAndAdminPreAuthorizeAreConsistent() throws ClassNotFoundException {
        List<Class<?>> controllers = scanRestControllers();
        assertFalse(controllers.isEmpty(), "com.kinn.app.controller配下に@RestControllerが1件も見つからない"
                + "(パッケージ名の変更等でこのテスト自体が意味を失っていないか確認すること)");

        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers) {
            boolean pathIsAdmin = firstRequestMappingPath(controller).startsWith(ADMIN_PATH_PREFIX);
            boolean requiresAdminRole = hasClassLevelAdminPreAuthorize(controller);

            if (pathIsAdmin && !requiresAdminRole) {
                violations.add(controller.getSimpleName()
                        + ": " + ADMIN_PATH_PREFIX + "配下にマッピングされているが、"
                        + "クラスレベルの @PreAuthorize(\"hasRole('ADMIN')\") が付いていない"
                        + "(SecurityConfigのURLパターンのみに認可が依存している)");
            }
            if (requiresAdminRole && !pathIsAdmin) {
                violations.add(controller.getSimpleName()
                        + ": @PreAuthorize(\"hasRole('ADMIN')\") が付いているが、"
                        + ADMIN_PATH_PREFIX + "配下にマッピングされていない"
                        + "(一般ユーザー向けAPIを誤って管理者専用化していないか確認すること)");
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "管理者API保護の一貫性違反が見つかりました:\n" + String.join("\n", violations));
    }

    private List<Class<?>> scanRestControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(CONTROLLER_BASE_PACKAGE)) {
            String className = ((AnnotatedBeanDefinition) beanDefinition)
                    .getMetadata().getClassName();
            result.add(Class.forName(className));
        }
        return result;
    }

    private String firstRequestMappingPath(Class<?> controller) {
        RequestMapping mapping = AnnotationUtils.findAnnotation(controller, RequestMapping.class);
        if (mapping == null) {
            return "";
        }
        String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
        return paths.length > 0 ? paths[0] : "";
    }

    private boolean hasClassLevelAdminPreAuthorize(Class<?> controller) {
        PreAuthorize preAuthorize = controller.getAnnotation(PreAuthorize.class);
        return preAuthorize != null && preAuthorize.value().contains("ADMIN");
    }
}
