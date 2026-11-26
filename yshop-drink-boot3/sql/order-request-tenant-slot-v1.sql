-- 租户关闭时，下单请求键也要被 uk_store_order_request 拦住。
-- 在已经执行 order-retry-pay-reconcile-v1.sql 的库上执行。可以重复执行。
--
-- MySQL 唯一索引不把 NULL 当成相同值。租户插件关闭时订单实体没有写入 tenant_id，
-- 插入后该列保持 NULL，同一用户、同一店、同一请求键的并发第二笔不会被拒绝。
-- tenant_id = 0 时第二笔会被 uk_store_order_request 拒绝。本脚本把空租户收成 0，并禁止再写入 NULL。
--
-- 新库：先导入 yixiang-drink-open.sql，再按顺序执行增量脚本。
-- 全量脚本里的 yshop_store_order.tenant_id 仍是 DEFAULT NULL，请求键和唯一索引也不在全量脚本里。
-- 新库要落盘这次修复，必须再执行 order-retry-pay-reconcile-v1.sql 和本文件。
-- 不要改全量脚本里的历史 INSERT。
--
-- 已经出现的重复键（空租户或 0，同一用户、同一店、同一请求键）只保留 id 最小的那一笔键。
-- 其余行清掉 request_key，订单行本身不删除，也不在这里回滚库存或记账。
-- 之后再用这个键会回到 id 最小的那一笔。request_key 为 NULL 的旧订单不参与唯一约束。

UPDATE `yshop_store_order` AS later
INNER JOIN `yshop_store_order` AS earlier
  ON earlier.uid = later.uid
 AND earlier.shop_id <=> later.shop_id
 AND earlier.request_key = later.request_key
 AND earlier.id < later.id
 AND IFNULL(earlier.tenant_id, 0) = IFNULL(later.tenant_id, 0)
SET later.request_key = NULL
WHERE later.request_key IS NOT NULL
  AND earlier.request_key IS NOT NULL;

UPDATE `yshop_store_order`
SET `tenant_id` = 0
WHERE `tenant_id` IS NULL;

SET @order_request_tenant_needs := (
  SELECT IF(IS_NULLABLE = 'YES' OR COLUMN_DEFAULT IS NULL OR COLUMN_DEFAULT <> '0', 1, 0)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'yshop_store_order'
    AND COLUMN_NAME = 'tenant_id'
);
SET @order_request_tenant_sql := IF(@order_request_tenant_needs = 1,
  'ALTER TABLE `yshop_store_order` MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT ''租户编号。关闭租户时为 0，请求键唯一索引才拦得住并发''',
  'DO 0');
PREPARE order_request_tenant_stmt FROM @order_request_tenant_sql;
EXECUTE order_request_tenant_stmt;
DEALLOCATE PREPARE order_request_tenant_stmt;
