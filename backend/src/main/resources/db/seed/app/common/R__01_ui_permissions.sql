-- Retired UI module: remove only its permission grants and catalog entries.
DELETE FROM auth_role_permission WHERE permission_code IN ('ui:screen:manage', 'ui:menu:manage');
DELETE FROM auth_permission WHERE permission_code IN ('ui:screen:manage', 'ui:menu:manage');
