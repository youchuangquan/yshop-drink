-- 店员工作台「预约接待与开桌」。在已经执行 hotpot-v1.sql 的库上执行一次。
-- 挂在商城中心下，只给已经拥有「门店管理查询」的角色。
-- 不新增权限，不新增收款、登记上菜或退菜菜单。
-- 页面调用的仍是 /store/hotpot/hold、/slot、/table、/seat、/session/open。

INSERT INTO `system_menu` (
  `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
  `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`
)
SELECT
  '预约接待与开桌', 'store:shop:query', 2, 15, 2000, 'hotpot-reception', 'ep:list',
  '/mall/store/hotpot/reception/index', 'HotpotReception', 0, b'1', b'1', b'1', '1', '1', b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu`
  WHERE `component` = '/mall/store/hotpot/reception/index' AND `deleted` = b'0'
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, menu.`id`, '1', '1', b'0', rm.`tenant_id`
FROM `system_role_menu` rm
JOIN `system_menu` menu
  ON menu.`component` = '/mall/store/hotpot/reception/index' AND menu.`deleted` = b'0'
WHERE rm.`menu_id` = 2265
  AND rm.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` existing
    WHERE existing.`role_id` = rm.`role_id`
      AND existing.`menu_id` = menu.`id`
      AND existing.`deleted` = b'0'
  );
