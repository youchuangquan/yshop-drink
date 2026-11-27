-- 麻辣烫报价、口味加料和付款后取餐号。在已经执行 shop-isolation-v1.sql、
-- order-retry-pay-reconcile-v1.sql 的库上执行一次。
-- packing_price 为空表示没配置打包费，不能当成 0。
-- 口味和加料的价格写在 yshop_shop_option.price，不能在下单时另编一个价。
-- 取餐号在付款入账之后写入。未付款的订单 number_id 保持为空。
-- 微信、支付宝仍未接通。晚到支付的处置口径 D1 没有在这里定义。

ALTER TABLE `yshop_store_shop`
  ADD COLUMN `packing_price` decimal(10,2) NULL DEFAULT NULL COMMENT '打包费。NULL 表示未配置，不能当成 0' AFTER `delivery_price`;

CREATE TABLE IF NOT EXISTS `yshop_shop_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `kind` varchar(16) NOT NULL COMMENT 'FLAVOR 口味，ADDON 加料',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `price` decimal(10,2) NOT NULL COMMENT '本店配置的价格',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_shop_option_shop` (`shop_id`, `kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店口味和加料';

ALTER TABLE `yshop_store_order`
  ADD COLUMN `packing_price` decimal(10,2) NULL DEFAULT NULL COMMENT '本单打包费。NULL 表示这次没有配置' AFTER `pay_postage`,
  ADD COLUMN `flavor_option_id` bigint NULL DEFAULT NULL COMMENT '口味选项' AFTER `packing_price`,
  ADD COLUMN `flavor_name` varchar(64) NULL DEFAULT NULL COMMENT '口味名称' AFTER `flavor_option_id`,
  ADD COLUMN `flavor_price` decimal(10,2) NULL DEFAULT NULL COMMENT '口味价格，来自门店配置' AFTER `flavor_name`,
  ADD COLUMN `addon_price` decimal(10,2) NULL DEFAULT NULL COMMENT '加料合计，来自门店配置' AFTER `flavor_price`;

CREATE TABLE IF NOT EXISTS `yshop_store_order_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `order_id` varchar(32) NOT NULL COMMENT '订单号',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `option_id` bigint NOT NULL COMMENT '门店选项 id',
  `kind` varchar(16) NOT NULL COMMENT 'FLAVOR 或 ADDON',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `unit_price` decimal(10,2) NOT NULL COMMENT '下单时的配置单价',
  `number` int NOT NULL COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '单价乘数量',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_order_option_shop` (`shop_id`, `order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单口味和加料';

ALTER TABLE `yshop_order_number`
  ADD COLUMN `shop_id` bigint NULL DEFAULT NULL COMMENT '门店 id。新取餐号必填，旧行可以空' AFTER `order_id`,
  ADD COLUMN `number_no` int NULL DEFAULT NULL COMMENT '本店取餐序号。付款后写入' AFTER `shop_id`,
  ADD UNIQUE KEY `uk_order_number_shop` (`shop_id`, `number_no`);
