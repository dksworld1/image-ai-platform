package com.imageaiplatform.ui.dao;

import com.imageaiplatform.ui.domain.ScreenPermission;
import com.imageaiplatform.ui.domain.UiMenu;
import com.imageaiplatform.ui.domain.UiScreen;
import java.util.List;

public interface UiDao {
    List<UiScreen> screens();
    UiScreen screen(String code);
    List<ScreenPermission> permissions();
    List<UiMenu> menus();
    List<UiMenu> lockMenus();
    int updateScreen(UiScreen screen);
    void insertScreen(UiScreen screen);
    int deleteScreen(String code);
    void deletePermissions(String code);
    void insertPermission(String screenCode, String permissionCode);
    int updateMenu(UiMenu menu);
    void insertMenu(UiMenu menu);
    int deleteMenu(String code);
}
