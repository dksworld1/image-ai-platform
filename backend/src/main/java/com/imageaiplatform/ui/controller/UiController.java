package com.imageaiplatform.ui.controller;

import com.imageaiplatform.auth.controller.PublicApi;
import com.imageaiplatform.auth.controller.RequirePermission;
import com.imageaiplatform.ui.domain.UiMenu;
import com.imageaiplatform.ui.domain.UiScreen;
import com.imageaiplatform.ui.service.UiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui")
public class UiController {
    private final UiService service;

    public UiController(UiService service) { this.service = service; }

    @GetMapping("/navigation")
    @PublicApi
    public NavigationResponse navigation(Authentication authentication) {
        var navigation = service.navigation(username(authentication));
        return new NavigationResponse(navigation.screens().stream().map(ScreenSummary::from).toList(),
                navigation.menus().stream().map(MenuResponse::from).toList());
    }

    @GetMapping("/screens/{code}")
    @PublicApi
    public ScreenSummary screen(@PathVariable String code, Authentication authentication) {
        return ScreenSummary.from(service.screenForUser(code, username(authentication)));
    }

    @GetMapping("/screens")
    @RequirePermission("ui:screen:manage")
    public List<ScreenResponse> screens() {
        return service.screens().stream().map(config -> {
            var screen = config.screen();
            return new ScreenResponse(screen.screenCode(), screen.displayName(), screen.routePath(),
                    screen.publicAccess(), screen.permissionRequired(), screen.enabled(), config.permissionCodes());
        }).toList();
    }

    @PutMapping("/screens/{code}")
    @RequirePermission("ui:screen:manage")
    public ResponseEntity<Void> saveScreen(@PathVariable String code, @Valid @RequestBody ScreenRequest request) {
        service.saveScreen(new UiScreen(code, request.displayName(), request.routePath(), request.publicAccess(),
                request.permissionRequired(), request.enabled()), request.permissionCodes());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/screens/{code}")
    @RequirePermission("ui:screen:manage")
    public ResponseEntity<Void> deleteScreen(@PathVariable String code) {
        service.deleteScreen(code);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menus")
    @RequirePermission("ui:menu:manage")
    public List<MenuResponse> menus() { return service.menus().stream().map(MenuResponse::from).toList(); }

    @PutMapping("/menus/{code}")
    @RequirePermission("ui:menu:manage")
    public ResponseEntity<Void> saveMenu(@PathVariable String code, @Valid @RequestBody MenuRequest request) {
        service.saveMenu(new UiMenu(code, request.parentMenuCode(), request.nodeType(), request.displayName(),
                request.screenCode(), request.sortOrder(), request.enabled()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/menus/{code}")
    @RequirePermission("ui:menu:manage")
    public ResponseEntity<Void> deleteMenu(@PathVariable String code) {
        service.deleteMenu(code);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class, NoSuchElementException.class,
            IllegalArgumentException.class, IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<Map<String, String>> error(RuntimeException exception) {
        if (exception instanceof AuthenticationException) { return error(401, "UNAUTHORIZED"); }
        if (exception instanceof AccessDeniedException) { return error(403, "FORBIDDEN"); }
        if (exception instanceof NoSuchElementException) { return error(404, "NOT_FOUND"); }
        if (exception instanceof IllegalArgumentException) { return error(400, "INVALID_REQUEST"); }
        if (exception instanceof DataIntegrityViolationException || exception instanceof IllegalStateException) {
            return error(409, "CONFLICT");
        }
        return error(500, "INTERNAL_SERVER_ERROR");
    }

    private ResponseEntity<Map<String, String>> error(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("error", code));
    }

    private String username(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                ? null : authentication.getName();
    }

    public record ScreenRequest(@NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 255) String routePath, @NotNull Boolean publicAccess,
            @NotNull Boolean permissionRequired, @NotNull Boolean enabled, @NotNull Set<String> permissionCodes) {}

    public record MenuRequest(String parentMenuCode, @NotBlank String nodeType,
            @NotBlank @Size(max = 100) String displayName, String screenCode,
            @NotNull @Min(0) Integer sortOrder, @NotNull Boolean enabled) {}

    public record ScreenSummary(String screenCode, String displayName, String routePath) {
        static ScreenSummary from(UiScreen screen) {
            return new ScreenSummary(screen.screenCode(), screen.displayName(), screen.routePath());
        }
    }

    public record ScreenResponse(String screenCode, String displayName, String routePath,
            Boolean publicAccess, Boolean permissionRequired, Boolean enabled, Set<String> permissionCodes) {}

    public record MenuResponse(String menuCode, String parentMenuCode, String nodeType, String displayName,
            String screenCode, Integer sortOrder, Boolean enabled) {
        static MenuResponse from(UiMenu menu) {
            return new MenuResponse(menu.menuCode(), menu.parentMenuCode(), menu.nodeType(), menu.displayName(),
                    menu.screenCode(), menu.sortOrder(), menu.enabled());
        }
    }

    public record NavigationResponse(List<ScreenSummary> screens, List<MenuResponse> menus) {}
}
