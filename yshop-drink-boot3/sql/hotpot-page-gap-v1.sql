-- 已经执行过 hotpot-v1.sql、但时段表还没有起止时刻的库，执行一次。
-- 新库直接执行更新后的 hotpot-v1.sql 即可，不必再跑本文件。
-- 起止时刻只供店员看是否已过点。这里不写保留时长，过点也不取消预约、不释放容量。
-- 菜品行记下单时的商品名称和标价。没有接通的收款渠道，这张表也不记录实付金额。

ALTER TABLE `yshop_hotpot_slot`
  ADD COLUMN `start_time` time NULL DEFAULT NULL COMMENT '时段开始。只供店员判断是否已过点，不自动取消' AFTER `occupied`,
  ADD COLUMN `end_time` time NULL DEFAULT NULL COMMENT '时段结束。已过点不释放容量，也不自动取消' AFTER `start_time`;

CREATE TABLE IF NOT EXISTS `yshop_hotpot_session_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `session_id` bigint NOT NULL COMMENT '开台',
  `product_id` bigint NOT NULL COMMENT '本店商品',
  `name` varchar(128) NOT NULL COMMENT '下单时的商品名称',
  `price_fen` int NOT NULL COMMENT '下单时的标价，分。不另编',
  `quantity` int NOT NULL COMMENT '份数',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hotpot_session_dish_shop` (`shop_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开台菜品';
