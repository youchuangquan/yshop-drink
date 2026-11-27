-- 下单防重和支付通知对账。在已经执行 shop-isolation-v1.sql 的库上执行一次。
-- request_key 为空的旧订单可以继续存在。空键不参与唯一约束，也不算防重。
-- 微信、支付宝这一轮没有接通。yshop_pay_notice 只记账，未接通的通知不能把订单改成已付。
-- 晚到支付（订单已取消、已退款、已完成或不存在）只记 LATE_HELD。不自动取消、不自动完成、不新增超时。

ALTER TABLE `yshop_store_order`
  ADD COLUMN `request_key` varchar(64) NULL DEFAULT NULL COMMENT '客户端稳定请求键。NULL 是旧客户端，不是防重' AFTER `order_id`;

ALTER TABLE `yshop_store_order`
  ADD UNIQUE KEY `uk_store_order_request` (`tenant_id`, `uid`, `shop_id`, `request_key`);

ALTER TABLE `yshop_score_order`
  ADD COLUMN `request_key` varchar(64) NULL DEFAULT NULL COMMENT '客户端稳定请求键。NULL 是旧客户端，不是防重' AFTER `order_id`;

ALTER TABLE `yshop_score_order`
  ADD UNIQUE KEY `uk_score_order_request` (`tenant_id`, `uid`, `shop_id`, `request_key`);

CREATE TABLE IF NOT EXISTS `yshop_pay_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `notify_key` varchar(128) NOT NULL COMMENT '支付通知稳定键',
  `order_id` varchar(64) NOT NULL COMMENT '订单号',
  `pay_type` varchar(32) NOT NULL COMMENT '支付方式',
  `disposition` varchar(32) NOT NULL COMMENT 'APPLIED REPLAY UNCONNECTED LATE_HELD',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_notice` (`notify_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付通知入账记录';
