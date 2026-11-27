-- 火锅预约、入座、开台和结账。在已经执行 shop-isolation-v1.sql、
-- order-retry-pay-reconcile-v1.sql、malatang-v1.sql 的库上执行一次。
-- 预约占时段容量，不占桌号。没有桌型，没有到店保留时长，也不做迟到自动取消。
-- 锅底价格只来自 yshop_shop_pot。没有配置就不编默认价。
-- 菜品价格只来自下单时的商品标价，写在 yshop_hotpot_session_dish。
-- 时段起止只给店员看是否已过点。过点不取消预约，也不释放容量。
-- 这一轮没有接通微信、支付宝、余额或现金。账单没有“已付金额”列，不能把未接通的支付写成已付。
-- 应付为 0 时释放的是开台时钉住的 slot_id。那一次不是支付成功。
-- 已经执行过本文件、但还没有 start_time 的库，再执行一次 hotpot-page-gap-v1.sql。
-- 已经执行过本文件、但菜品行还没有 served_qty 的库，再执行一次 hotpot-serve-refund-v1.sql。

CREATE TABLE IF NOT EXISTS `yshop_hotpot_slot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `capacity` int NOT NULL COMMENT '可占用容量',
  `occupied` int NOT NULL DEFAULT '0' COMMENT '已占用。预约和入座、散客各算一次，入座不重复加',
  `start_time` time NULL DEFAULT NULL COMMENT '时段开始。只供店员判断是否已过点，不自动取消',
  `end_time` time NULL DEFAULT NULL COMMENT '时段结束。已过点不释放容量，也不自动取消',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hotpot_slot_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火锅时段容量';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_table` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `sign_code` varchar(64) NOT NULL COMMENT '桌台签码',
  `occupied` tinyint NOT NULL DEFAULT '0' COMMENT '1 占用，0 空闲',
  `bound_uid` bigint NULL DEFAULT NULL COMMENT '绑定的顾客。公开扫码不返回',
  `session_id` bigint NULL DEFAULT NULL COMMENT '进行中的开台',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hotpot_table_shop_sign` (`shop_id`, `sign_code`),
  KEY `idx_hotpot_table_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火锅桌台';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_hold` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `slot_id` bigint NOT NULL COMMENT '占用的时段',
  `owner_uid` bigint NOT NULL COMMENT '预约人或散客',
  `request_key` varchar(64) NOT NULL COMMENT '创建这次占用的请求键',
  `status` varchar(16) NOT NULL COMMENT 'HELD、SEATED、CANCELLED、RELEASED',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hotpot_hold_shop_key` (`shop_id`, `request_key`),
  KEY `idx_hotpot_hold_shop` (`shop_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火锅占位';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `slot_id` bigint NOT NULL COMMENT '开台时钉住的时段',
  `table_id` bigint NULL DEFAULT NULL COMMENT '桌台',
  `hold_id` bigint NULL DEFAULT NULL COMMENT '覆盖这次开台的占位',
  `owner_uid` bigint NOT NULL DEFAULT '0' COMMENT '顾客。0 表示没有绑定顾客',
  `version` int NOT NULL COMMENT '结账版本',
  `payable_fen` int NOT NULL DEFAULT '0' COMMENT '应付，分。未退菜品按原价；免单后不再计锅底。只是缓存',
  `zero_pay` tinyint NOT NULL DEFAULT '0' COMMENT '1 表示应付为 0 已释放。不是支付成功',
  `fees_waived` tinyint NOT NULL DEFAULT '0' COMMENT '1 表示锅底费不再计入应付。不删除锅底行',
  `status` varchar(16) NOT NULL COMMENT 'OPEN 或 RELEASED',
  `request_key` varchar(64) NOT NULL COMMENT '开台请求键',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hotpot_session_shop` (`shop_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火锅开台';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_session_pot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `session_id` bigint NOT NULL COMMENT '开台',
  `pot_id` bigint NOT NULL COMMENT '门店锅底',
  `name` varchar(64) NOT NULL COMMENT '下单时的锅底名称',
  `price_fen` int NOT NULL COMMENT '下单时的配置价格，分',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hotpot_session_pot_shop` (`shop_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开台锅底';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_session_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `session_id` bigint NOT NULL COMMENT '开台',
  `product_id` bigint NOT NULL COMMENT '本店商品',
  `name` varchar(128) NOT NULL COMMENT '下单时的商品名称',
  `price_fen` int NOT NULL COMMENT '下单时的标价，分。不另编',
  `quantity` int NOT NULL COMMENT '下单份数。上菜和退菜都不改它',
  `served_qty` int NOT NULL DEFAULT '0' COMMENT '已上桌份数。不减少原始 quantity',
  `refunded_qty` int NOT NULL DEFAULT '0' COMMENT '未上桌已退份数。已上桌的不能退',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_hotpot_session_dish_shop` (`shop_id`, `session_id`),
  CONSTRAINT `chk_hotpot_dish_served_refunded` CHECK (`served_qty` >= 0 AND `refunded_qty` >= 0 AND `served_qty` + `refunded_qty` <= `quantity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开台菜品';

CREATE TABLE IF NOT EXISTS `yshop_shop_pot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `name` varchar(64) NOT NULL COMMENT '锅底名称',
  `price_fen` int NOT NULL COMMENT '本店价格，分。不能为空',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_shop_pot_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店锅底';

CREATE TABLE IF NOT EXISTS `yshop_hotpot_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `shop_id` bigint NOT NULL COMMENT '门店 id',
  `request_key` varchar(64) NOT NULL COMMENT '请求键',
  `owner_uid` bigint NOT NULL COMMENT '键所属的人',
  `slot_id` bigint NOT NULL DEFAULT '0' COMMENT '键绑定的时段。0 表示这次不是占时段',
  `action` varchar(16) NOT NULL COMMENT 'RESERVE、SEAT、SEAT_TABLE、WALKIN、OPEN、ADD_POT、DISH、SLOT、TABLE、POT',
  `hold_id` bigint NULL DEFAULT NULL COMMENT '占位',
  `session_id` bigint NULL DEFAULT NULL COMMENT '开台',
  `pot_id` bigint NULL DEFAULT NULL COMMENT '加过的锅底',
  `resource_id` bigint NULL DEFAULT NULL COMMENT '新建的时段、桌台或锅底',
  `mark` varchar(128) NULL DEFAULT NULL COMMENT '配置内容。相同的键不能改成另一份',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hotpot_request_shop_key` (`shop_id`, `request_key`),
  KEY `idx_hotpot_request_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火锅写请求键';
