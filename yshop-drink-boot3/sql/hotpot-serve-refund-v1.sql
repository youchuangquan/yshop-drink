-- 登记上菜与未上桌退菜。在已经执行 hotpot-v1.sql（或随后的 hotpot-page-gap-v1.sql）的库上可以重复执行。
-- 新库直接执行更新后的 hotpot-v1.sql 即可，不必再跑本文件。
-- quantity 仍是下单份数，不在退菜或上菜时改小。
-- served_qty 是已上桌份数，refunded_qty 是未上桌已退份数，两者之和不能超过 quantity。
-- fees_waived 表示这台的锅底费不再计入应付。不删除锅底行。没有陪客费列，这里不新建。
-- 应付变成 0 后的释放仍写 zero_pay。那一次不是支付成功。
-- 列或检查约束已经存在时，对应语句变成 DO 0，不再 ALTER。

SET @hotpot_served_qty_missing := (
  SELECT IF(COUNT(*) = 0, 1, 0)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'yshop_hotpot_session_dish'
    AND COLUMN_NAME = 'served_qty'
);
SET @hotpot_served_qty_sql := IF(@hotpot_served_qty_missing = 1,
  'ALTER TABLE `yshop_hotpot_session_dish` ADD COLUMN `served_qty` int NOT NULL DEFAULT ''0'' COMMENT ''已上桌份数。不减少原始 quantity'' AFTER `quantity`',
  'DO 0');
PREPARE hotpot_served_qty_stmt FROM @hotpot_served_qty_sql;
EXECUTE hotpot_served_qty_stmt;
DEALLOCATE PREPARE hotpot_served_qty_stmt;

SET @hotpot_refunded_qty_missing := (
  SELECT IF(COUNT(*) = 0, 1, 0)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'yshop_hotpot_session_dish'
    AND COLUMN_NAME = 'refunded_qty'
);
SET @hotpot_refunded_qty_sql := IF(@hotpot_refunded_qty_missing = 1,
  'ALTER TABLE `yshop_hotpot_session_dish` ADD COLUMN `refunded_qty` int NOT NULL DEFAULT ''0'' COMMENT ''未上桌已退份数。已上桌的不能退'' AFTER `served_qty`',
  'DO 0');
PREPARE hotpot_refunded_qty_stmt FROM @hotpot_refunded_qty_sql;
EXECUTE hotpot_refunded_qty_stmt;
DEALLOCATE PREPARE hotpot_refunded_qty_stmt;

SET @hotpot_dish_check_missing := (
  SELECT IF(COUNT(*) = 0, 1, 0)
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'yshop_hotpot_session_dish'
    AND CONSTRAINT_NAME = 'chk_hotpot_dish_served_refunded'
    AND CONSTRAINT_TYPE = 'CHECK'
);
SET @hotpot_dish_check_sql := IF(@hotpot_dish_check_missing = 1,
  'ALTER TABLE `yshop_hotpot_session_dish` ADD CONSTRAINT `chk_hotpot_dish_served_refunded` CHECK (`served_qty` >= 0 AND `refunded_qty` >= 0 AND `served_qty` + `refunded_qty` <= `quantity`)',
  'DO 0');
PREPARE hotpot_dish_check_stmt FROM @hotpot_dish_check_sql;
EXECUTE hotpot_dish_check_stmt;
DEALLOCATE PREPARE hotpot_dish_check_stmt;

SET @hotpot_fees_waived_missing := (
  SELECT IF(COUNT(*) = 0, 1, 0)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'yshop_hotpot_session'
    AND COLUMN_NAME = 'fees_waived'
);
SET @hotpot_fees_waived_sql := IF(@hotpot_fees_waived_missing = 1,
  'ALTER TABLE `yshop_hotpot_session` ADD COLUMN `fees_waived` tinyint NOT NULL DEFAULT ''0'' COMMENT ''1 表示锅底费不再计入应付。不删除锅底行'' AFTER `zero_pay`',
  'DO 0');
PREPARE hotpot_fees_waived_stmt FROM @hotpot_fees_waived_sql;
EXECUTE hotpot_fees_waived_stmt;
DEALLOCATE PREPARE hotpot_fees_waived_stmt;
