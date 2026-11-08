package com.imageaiplatform.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.imageaiplatform.auth.service.JwtTokenService;
import com.imageaiplatform.ui.service.UiService;
import jakarta.servlet.Filter;
import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "datasource.app.url=jdbc:h2:mem:ui-api;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class UiApiTest {
    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter security;
    @Autowired @Qualifier("appDataSource") private DataSource source;
    @Autowired private JwtTokenService tokens;
    @Autowired private UiService service;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private String manager;
    private String viewer;
    private String plain;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(source);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        // HTTP 요청별 실제 커밋·롤백을 검증하므로 테스트 전체를 한 트랜잭션으로 감싸지 않습니다.
        while (jdbc.queryForObject("SELECT COUNT(*) FROM ui_menu", Integer.class) > 0) {
            assertThat(jdbc.update("DELETE FROM ui_menu WHERE menu_code NOT IN (SELECT parent_menu_code FROM ui_menu WHERE parent_menu_code IS NOT NULL)"))
                    .isGreaterThan(0);
        }
        jdbc.update("DELETE FROM ui_screen_permission");
        jdbc.update("DELETE FROM ui_screen");
        jdbc.update("DELETE FROM auth_user_role");
        jdbc.update("DELETE FROM auth_role_permission");
        jdbc.update("DELETE FROM auth_role");
        jdbc.update("DELETE FROM auth_user");
        jdbc.update("DELETE FROM auth_permission WHERE permission_code LIKE 'dataset:%'");
        jdbc.update("INSERT INTO auth_user VALUES ('ui-manager', 'unused', TRUE), ('viewer', 'unused', TRUE), ('plain', 'unused', TRUE)");
        jdbc.update("INSERT INTO auth_role (role_code, display_name) VALUES ('UI_MANAGER', 'Manager'), ('UI_VIEWER', 'Viewer')");
        jdbc.update("INSERT INTO auth_permission (permission_code, display_name) VALUES ('dataset:item:read', 'Read'), ('dataset:item:write', 'Write')");
        jdbc.update("INSERT INTO auth_user_role VALUES ('ui-manager', 'UI_MANAGER'), ('viewer', 'UI_VIEWER')");
        jdbc.update("INSERT INTO auth_role_permission VALUES ('UI_MANAGER', 'ui:screen:manage'), ('UI_MANAGER', 'ui:menu:manage'), ('UI_VIEWER', 'dataset:item:read')");
        jdbc.update("INSERT INTO ui_screen VALUES ('home', 'Home', '/', TRUE, FALSE, TRUE), ('profile', 'Profile', '/profile', FALSE, FALSE, TRUE), ('datasets', 'Datasets', '/datasets', FALSE, TRUE, TRUE), ('locked', 'Locked', '/locked', FALSE, TRUE, TRUE), ('disabled', 'Disabled', '/disabled', TRUE, FALSE, FALSE)");
        jdbc.update("INSERT INTO ui_screen_permission VALUES ('datasets', 'dataset:item:read'), ('datasets', 'dataset:item:write')");
        jdbc.update("INSERT INTO ui_menu VALUES ('home', NULL, 'SCREEN', 'Home', 'home', 0, TRUE), ('workspace', NULL, 'GROUP', 'Workspace', NULL, 1, TRUE), ('data', 'workspace', 'SCREEN', 'Data', 'datasets', 0, TRUE), ('empty', NULL, 'GROUP', 'Empty', NULL, 2, TRUE), ('off', NULL, 'GROUP', 'Off', NULL, 3, FALSE), ('off-home', 'off', 'SCREEN', 'Hidden home', 'home', 0, TRUE)");
        manager = bearer("ui-manager");
        viewer = bearer("viewer");
        plain = bearer("plain");
    }

    @Test
    void anonymousNavigationOnlyContainsPublicScreensAndVisibleMenus() throws Exception {
        mvc.perform(get("/api/ui/navigation")).andExpect(status().isOk())
                .andExpect(jsonPath("$.screens[*].screenCode", hasItem("home")))
                .andExpect(jsonPath("$.screens[*].screenCode", not(hasItem("profile"))))
                .andExpect(jsonPath("$.screens[*].screenCode", not(hasItem("disabled"))))
                .andExpect(jsonPath("$.menus.length()").value(1))
                .andExpect(jsonPath("$.menus[0].menuCode").value("home"));
    }

    @Test
    void anyRepresentativePermissionShowsScreenAndAncestorsWithoutDuplicateRows() throws Exception {
        mvc.perform(get("/api/ui/navigation").header("Authorization", viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.screens[*].screenCode", hasItem("datasets")))
                .andExpect(jsonPath("$.screens[*].screenCode", hasItem("profile")))
                .andExpect(jsonPath("$.screens[*].screenCode", not(hasItem("locked"))))
                .andExpect(jsonPath("$.menus[*].menuCode", hasItem("workspace")))
                .andExpect(jsonPath("$.menus[*].menuCode", hasItem("data")))
                .andExpect(jsonPath("$.menus[*].menuCode", not(hasItem("off-home"))));
        jdbc.update("INSERT INTO auth_role_permission VALUES ('UI_VIEWER', 'dataset:item:write')");
        var navigation = service.navigation("viewer");
        assertThat(navigation.screens().stream().filter(screen -> screen.screenCode().equals("datasets")).count()).isEqualTo(1);
    }

    @Test
    void activeAccountWithoutRolesCanOpenAuthenticationOnlyScreenButCannotImpersonateViewer() throws Exception {
        mvc.perform(get("/api/ui/screens/profile").header("Authorization", plain)).andExpect(status().isOk());
        mvc.perform(get("/api/ui/navigation?username=viewer").header("Authorization", plain))
                .andExpect(jsonPath("$.screens[*].screenCode", not(hasItem("datasets"))));
    }

    @Test
    void screenAccessDistinguishesAuthenticationPermissionAndDisabledScreen() throws Exception {
        mvc.perform(get("/api/ui/screens/home")).andExpect(status().isOk());
        mvc.perform(get("/api/ui/screens/datasets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ui/screens/datasets").header("Authorization", plain)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ui/screens/datasets").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/ui/screens/locked").header("Authorization", viewer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ui/screens/disabled")).andExpect(status().isNotFound());
    }

    @Test
    void revocationAndAccountDisablingAreReflectedOnNextRequest() throws Exception {
        jdbc.update("DELETE FROM auth_user_role WHERE username = 'viewer'");
        mvc.perform(get("/api/ui/navigation").header("Authorization", viewer))
                .andExpect(jsonPath("$.menus[*].menuCode", not(hasItem("workspace"))));
        jdbc.update("UPDATE auth_user SET enabled = FALSE WHERE username = 'viewer'");
        mvc.perform(get("/api/ui/navigation").header("Authorization", viewer)).andExpect(status().isUnauthorized());
    }

    @Test
    void managementRequiresSpecificPermissionsAndNeverMutatesOnDenial() throws Exception {
        mvc.perform(put("/api/ui/screens/new").contentType("application/json").content(screenBody("New", "/new", "[]")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/ui/screens/new").header("Authorization", viewer).contentType("application/json")
                        .content(screenBody("New", "/new", "[]"))).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO auth_role_permission VALUES ('UI_VIEWER', 'ui:screen:manage')");
        mvc.perform(get("/api/ui/screens").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/ui/menus").header("Authorization", viewer)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen WHERE screen_code = 'new'", Integer.class)).isZero();
    }

    @Test
    void createsUpdatesAndDeletesScreenMenuAndBindings() throws Exception {
        saveScreen("new", screenBody("New", "/new", "[\"dataset:item:read\"]"), 204);
        saveMenu("new-group", menuBody(null, "GROUP", null), 204);
        saveMenu("new-menu", menuBody("new-group", "SCREEN", "new"), 204);
        mvc.perform(get("/api/ui/navigation").header("Authorization", viewer))
                .andExpect(jsonPath("$.menus[*].menuCode", hasItem("new-menu")));
        saveScreen("new", screenBody("Renamed", "/renamed", "[\"dataset:item:write\"]"), 204);
        mvc.perform(get("/api/ui/screens").header("Authorization", manager)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT display_name FROM ui_screen WHERE screen_code = 'new'", String.class)).isEqualTo("Renamed");
        mvc.perform(delete("/api/ui/menus/new-menu").header("Authorization", manager)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/ui/menus/new-group").header("Authorization", manager)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/ui/screens/new").header("Authorization", manager)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen_permission WHERE screen_code = 'new'", Integer.class)).isZero();
    }

    @Test
    void failedPermissionReplacementRollsBackScreenAndExistingBindings() throws Exception {
        saveScreen("datasets", screenBody("Wrong change", "/changed", "[\"missing:item:read\"]"), 409);
        assertThat(jdbc.queryForObject("SELECT route_path FROM ui_screen WHERE screen_code = 'datasets'", String.class)).isEqualTo("/datasets");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen_permission WHERE screen_code = 'datasets'", Integer.class)).isEqualTo(2);
    }

    @Test
    void deletingReferencedScreenOrGroupIsAtomicConflict() throws Exception {
        mvc.perform(delete("/api/ui/screens/datasets").header("Authorization", manager)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_screen_permission WHERE screen_code = 'datasets'", Integer.class)).isEqualTo(2);
        mvc.perform(delete("/api/ui/menus/workspace").header("Authorization", manager)).andExpect(status().isConflict());
    }

    @Test
    void forbidsCyclesLeafParentsAndConvertingGroupWithChildren() throws Exception {
        saveMenu("sub-group", menuBody("workspace", "GROUP", null), 204);
        saveMenu("workspace", menuBody("sub-group", "GROUP", null), 400);
        saveMenu("invalid", menuBody("data", "GROUP", null), 400);
        saveMenu("workspace", menuBody(null, "SCREEN", "home"), 409);
        saveMenu("self", menuBody("self", "GROUP", null), 400);
    }

    @Test
    void concurrentReparentingCannotCreateCycle() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timed out"); }
                return mvc.perform(put("/api/ui/menus/workspace").header("Authorization", manager)
                                .contentType("application/json").content(menuBody("empty", "GROUP", null)))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("Start timed out"); }
                return mvc.perform(put("/api/ui/menus/empty").header("Authorization", manager)
                                .contentType("application/json").content(menuBody("workspace", "GROUP", null)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 400);
        }
    }

    @Test
    void validatesRoutePolicyAndNullableRequestValues() throws Exception {
        saveScreen("new", screenBody("New", "https://outside.example", "[]"), 400);
        saveScreen("new", screenBody("New", "/new", "[]").replace("\"publicAccess\":false", "\"publicAccess\":true"), 400);
        saveScreen("new", screenBody("New", "/new", "[\"dataset:item:read\"]").replace("\"permissionRequired\":true", "\"permissionRequired\":false"), 400);
        saveScreen("new", screenBody("New", "/new", "[]").replace("\"enabled\":true", "\"enabled\":null"), 400);
        saveMenu("new-menu", menuBody(null, "SCREEN", "missing-screen"), 400);
    }

    @Test
    void disabledAncestorHidesMenusWithoutRevokingIndependentScreenAccess() throws Exception {
        jdbc.update("UPDATE ui_menu SET enabled = FALSE WHERE menu_code = 'workspace'");
        mvc.perform(get("/api/ui/navigation").header("Authorization", viewer))
                .andExpect(jsonPath("$.menus[*].menuCode", not(hasItem("data"))))
                .andExpect(jsonPath("$.screens[*].screenCode", hasItem("datasets")));
        mvc.perform(get("/api/ui/screens/datasets").header("Authorization", viewer)).andExpect(status().isOk());
    }

    private void saveScreen(String code, String body, int status) throws Exception {
        mvc.perform(put("/api/ui/screens/" + code).header("Authorization", manager).contentType("application/json").content(body))
                .andExpect(status().is(status));
    }

    private void saveMenu(String code, String body, int status) throws Exception {
        mvc.perform(put("/api/ui/menus/" + code).header("Authorization", manager).contentType("application/json").content(body))
                .andExpect(status().is(status));
    }

    private String screenBody(String name, String path, String permissions) {
        return "{\"displayName\":\"%s\",\"routePath\":\"%s\",\"publicAccess\":false,\"permissionRequired\":true,\"enabled\":true,\"permissionCodes\":%s}".formatted(name, path, permissions);
    }

    private String menuBody(String parent, String type, String screen) {
        return "{\"parentMenuCode\":%s,\"nodeType\":\"%s\",\"displayName\":\"Menu\",\"screenCode\":%s,\"sortOrder\":0,\"enabled\":true}"
                .formatted(parent == null ? "null" : "\"" + parent + "\"", type, screen == null ? "null" : "\"" + screen + "\"");
    }

    private String bearer(String username) { return "Bearer " + tokens.issue(username).value(); }
}
