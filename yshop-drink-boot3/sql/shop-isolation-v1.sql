-- 两店隔离增量脚本。在已导入 yixiang-drink-open.sql 的库上执行。
-- 不回填历史积分：yshop_user.integral 没有门店归属，不能猜该划给哪一家店。

CREATE TABLE IF NOT EXISTS `yshop_user_shop_integral` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uid` bigint NOT NULL COMMENT '用户id',
  `shop_id` bigint NOT NULL COMMENT '门店id',
  `integral` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本店积分',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_shop` (`uid`, `shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户门店积分';

ALTER TABLE `yshop_user_bill`
  ADD COLUMN `shop_id` bigint NULL DEFAULT NULL COMMENT '门店id，积分流水必填' AFTER `uid`;

ALTER TABLE `yshop_score_product`
  ADD COLUMN `shop_id` bigint NULL DEFAULT NULL COMMENT '门店id' AFTER `id`;

ALTER TABLE `yshop_score_order`
  ADD COLUMN `shop_id` bigint NULL DEFAULT NULL COMMENT '门店id' AFTER `uid`;
