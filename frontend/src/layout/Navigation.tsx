import type { UiMenu, UiNavigation } from '../api/ui'

export default function Navigation({ navigation }: { navigation: UiNavigation }) {
  const screens = new Map(navigation.screens.map(screen => [screen.screenCode, screen]))
  const children = new Map<string | null, UiMenu[]>()
  for (const menu of navigation.menus) {
    const siblings = children.get(menu.parentMenuCode) ?? []
    siblings.push(menu)
    children.set(menu.parentMenuCode, siblings)
  }
  for (const siblings of children.values()) {
    siblings.sort((a, b) => a.sortOrder - b.sortOrder || (a.menuCode < b.menuCode ? -1 : a.menuCode > b.menuCode ? 1 : 0))
  }

  function renderMenu(menu: UiMenu) {
    if (menu.nodeType === 'GROUP') {
      return (
        <li key={menu.menuCode}>
          <span className="nav-group-title">{menu.displayName}</span>
          <ul className="nav-children">{children.get(menu.menuCode)?.map(renderMenu)}</ul>
        </li>
      )
    }
    const screen = screens.get(menu.screenCode ?? '')
    if (!screen) return null
    return (
      <li key={menu.menuCode}>
        {screen.routePath === '/' ? (
          <a className="nav-link" href="/" aria-current="page" onClick={event => {
            if (event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey && !event.altKey) event.preventDefault()
          }}>{menu.displayName}</a>
        ) : (
          <span className="nav-link nav-unavailable" aria-disabled="true">{menu.displayName}<small>준비 중</small></span>
        )}
      </li>
    )
  }

  if (navigation.menus.length === 0) return <p className="nav-message">표시할 메뉴가 없습니다.</p>
  return <ul className="nav-tree">{children.get(null)?.map(renderMenu)}</ul>
}
