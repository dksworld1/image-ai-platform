package com.imageaiplatform.ui.dao.impl;

import com.imageaiplatform.ui.dao.UiDao;
import com.imageaiplatform.ui.domain.ScreenPermission;
import com.imageaiplatform.ui.domain.UiMenu;
import com.imageaiplatform.ui.domain.UiScreen;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisUiDao implements UiDao {
    private final SqlSession sql;

    public MyBatisUiDao(@Qualifier("appSqlSessionTemplate") SqlSession sql) { this.sql = sql; }

    public List<UiScreen> screens() { return sql.selectList("ui.screens"); }
    public UiScreen screen(String code) { return sql.selectOne("ui.screen", code); }
    public List<ScreenPermission> permissions() { return sql.selectList("ui.permissions"); }
    public List<UiMenu> menus() { return sql.selectList("ui.menus"); }
    public List<UiMenu> lockMenus() { return sql.selectList("ui.lockMenus"); }
    public int updateScreen(UiScreen screen) { return sql.update("ui.updateScreen", screen); }
    public void insertScreen(UiScreen screen) { sql.insert("ui.insertScreen", screen); }
    public int deleteScreen(String code) { return sql.delete("ui.deleteScreen", code); }
    public void deletePermissions(String code) { sql.delete("ui.deletePermissions", code); }
    public void insertPermission(String screenCode, String permissionCode) {
        sql.insert("ui.insertPermission", Map.of("screenCode", screenCode, "permissionCode", permissionCode));
    }
    public int updateMenu(UiMenu menu) { return sql.update("ui.updateMenu", menu); }
    public void insertMenu(UiMenu menu) { sql.insert("ui.insertMenu", menu); }
    public int deleteMenu(String code) { return sql.delete("ui.deleteMenu", code); }
}
