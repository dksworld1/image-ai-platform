package com.imageaiplatform.ui.service;

import com.imageaiplatform.auth.service.AuthorizationService;
import com.imageaiplatform.ui.dao.UiDao;
import com.imageaiplatform.ui.domain.UiMenu;
import com.imageaiplatform.ui.domain.UiNavigation;
import com.imageaiplatform.ui.domain.UiScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(transactionManager = "appTransactionManager", readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class UiService {
    private final UiDao dao;
    private final AuthorizationService authorization;

    public UiService(UiDao dao, AuthorizationService authorization) {
        this.dao = dao;
        this.authorization = authorization;
    }

    public UiNavigation navigation(String username) {
        Set<String> granted = username == null ? Set.of() : authorization.effectivePermissions(username);
        Map<String, Set<String>> conditions = conditions();
        List<UiScreen> screens = dao.screens().stream()
                .filter(screen -> canOpen(screen, username, granted, conditions)).toList();
        Set<String> allowedScreens = screens.stream().map(UiScreen::screenCode).collect(Collectors.toSet());
        List<UiMenu> menus = dao.menus();
        Map<String, List<UiMenu>> children = new HashMap<>();
        for (UiMenu menu : menus) {
            children.computeIfAbsent(menu.parentMenuCode(), ignored -> new ArrayList<>()).add(menu);
        }
        Set<String> visible = new HashSet<>();
        for (UiMenu root : children.getOrDefault(null, List.of())) {
            markVisible(root, children, allowedScreens, visible);
        }
        return new UiNavigation(screens, menus.stream().filter(menu -> visible.contains(menu.menuCode())).toList());
    }

    public UiScreen screenForUser(String code, String username) {
        UiScreen screen = dao.screen(code);
        if (screen == null || !Boolean.TRUE.equals(screen.enabled())) {
            throw new NoSuchElementException("Screen not found.");
        }
        if (!Boolean.TRUE.equals(screen.publicAccess()) && username == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        Set<String> granted = username == null ? Set.of() : authorization.effectivePermissions(username);
        if (!canOpen(screen, username, granted, conditions())) {
            throw new AccessDeniedException("Screen access is not granted.");
        }
        return screen;
    }

    @PreAuthorize("@authorizationService.hasPermission('ui:screen:manage')")
    public List<UiScreen.Configuration> screens() {
        Map<String, Set<String>> conditions = conditions();
        return dao.screens().stream()
                .map(screen -> new UiScreen.Configuration(screen, conditions.getOrDefault(screen.screenCode(), Set.of())))
                .toList();
    }

    @PreAuthorize("@authorizationService.hasPermission('ui:menu:manage')")
    public List<UiMenu> menus() { return dao.menus(); }

    @Transactional(transactionManager = "appTransactionManager")
    @PreAuthorize("@authorizationService.hasPermission('ui:screen:manage')")
    public void saveScreen(UiScreen screen, Set<String> permissions) {
        validateCode(screen.screenCode());
        if (screen.displayName() == null || screen.displayName().isBlank() || screen.displayName().length() > 100
                || screen.routePath() == null || screen.routePath().length() > 255
                || !screen.routePath().matches("/(?:[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*)?")
                || screen.publicAccess() == null || screen.permissionRequired() == null || screen.enabled() == null
                || permissions == null || (Boolean.TRUE.equals(screen.publicAccess()) && Boolean.TRUE.equals(screen.permissionRequired()))
                || (Boolean.FALSE.equals(screen.permissionRequired()) && !permissions.isEmpty())) {
            throw new IllegalArgumentException("Invalid screen policy or internal route.");
        }
        for (String permission : permissions) {
            if (permission == null || permission.length() > 150
                    || !permission.matches("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*")) {
                throw new IllegalArgumentException("Invalid screen permission.");
            }
        }
        if (dao.updateScreen(screen) == 0) { dao.insertScreen(screen); }
        dao.deletePermissions(screen.screenCode());
        for (String permission : permissions) { dao.insertPermission(screen.screenCode(), permission); }
    }

    @Transactional(transactionManager = "appTransactionManager")
    @PreAuthorize("@authorizationService.hasPermission('ui:screen:manage')")
    public void deleteScreen(String code) {
        validateCode(code);
        dao.deletePermissions(code);
        if (dao.deleteScreen(code) == 0) { throw new NoSuchElementException("Screen not found."); }
    }

    @Transactional(transactionManager = "appTransactionManager")
    @PreAuthorize("@authorizationService.hasPermission('ui:menu:manage')")
    public void saveMenu(UiMenu menu) {
        validateCode(menu.menuCode());
        if (menu.displayName() == null || menu.displayName().isBlank() || menu.displayName().length() > 100
                || menu.sortOrder() == null || menu.sortOrder() < 0 || menu.enabled() == null
                || !("GROUP".equals(menu.nodeType()) || "SCREEN".equals(menu.nodeType()))
                || ("GROUP".equals(menu.nodeType()) != (menu.screenCode() == null))) {
            throw new IllegalArgumentException("Invalid menu node.");
        }
        Map<String, UiMenu> existing = dao.lockMenus().stream().collect(Collectors.toMap(UiMenu::menuCode, Function.identity()));
        String parent = menu.parentMenuCode();
        Set<String> visited = new HashSet<>();
        while (parent != null) {
            if (parent.equals(menu.menuCode()) || !visited.add(parent)) {
                throw new IllegalArgumentException("Menu cycle is not allowed.");
            }
            UiMenu ancestor = existing.get(parent);
            if (ancestor == null || !"GROUP".equals(ancestor.nodeType())) {
                throw new IllegalArgumentException("Parent must be an existing GROUP.");
            }
            parent = ancestor.parentMenuCode();
        }
        if ("SCREEN".equals(menu.nodeType())) {
            if (dao.screen(menu.screenCode()) == null) { throw new IllegalArgumentException("Screen does not exist."); }
            if (existing.values().stream().anyMatch(node -> Objects.equals(node.parentMenuCode(), menu.menuCode()))) {
                throw new IllegalStateException("A group with children cannot become a screen menu.");
            }
        }
        if (dao.updateMenu(menu) == 0) { dao.insertMenu(menu); }
    }

    @Transactional(transactionManager = "appTransactionManager")
    @PreAuthorize("@authorizationService.hasPermission('ui:menu:manage')")
    public void deleteMenu(String code) {
        validateCode(code);
        dao.lockMenus();
        if (dao.deleteMenu(code) == 0) { throw new NoSuchElementException("Menu not found."); }
    }

    private Map<String, Set<String>> conditions() {
        Map<String, Set<String>> result = new HashMap<>();
        dao.permissions().forEach(rule -> result.computeIfAbsent(rule.screenCode(), ignored -> new HashSet<>()).add(rule.permissionCode()));
        return result;
    }

    private boolean canOpen(UiScreen screen, String username, Set<String> granted, Map<String, Set<String>> conditions) {
        return Boolean.TRUE.equals(screen.enabled()) && (Boolean.TRUE.equals(screen.publicAccess())
                || (username != null && (Boolean.FALSE.equals(screen.permissionRequired())
                || conditions.getOrDefault(screen.screenCode(), Set.of()).stream().anyMatch(granted::contains))));
    }

    private boolean markVisible(UiMenu menu, Map<String, List<UiMenu>> children, Set<String> screens, Set<String> visible) {
        if (!Boolean.TRUE.equals(menu.enabled())) { return false; }
        boolean show = false;
        if ("SCREEN".equals(menu.nodeType())) {
            show = screens.contains(menu.screenCode());
        } else {
            for (UiMenu child : children.getOrDefault(menu.menuCode(), List.of())) {
                show = markVisible(child, children, screens, visible) || show;
            }
        }
        if (show) { visible.add(menu.menuCode()); }
        return show;
    }

    private void validateCode(String code) {
        if (code == null || code.length() > 100 || !code.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid screen or menu code.");
        }
    }
}
