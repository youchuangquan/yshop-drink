-- 登记上菜与未上桌退菜。在已经执行 hotpot-v1.sql（或随后的 hotpot-page-gap-v1.sql）的库上执行一次。
-- 新库直接执行更新后的 hotpot-v1.sql 即可，不必再跑本文件。
-- quantity 仍是下单份数，不在退菜或上菜时改小。
-- served_qty 是已上桌份数，refunded_qty 是未上桌已退份数，两者之和不能超过 quantity。
-- fees_waived 表示这台的锅底费不再计入应付。不删除锅底行。没有陪客费字段，这里不新建。
-- 应付变成 0 后的释放仍写 zero_pay。那一次不是支付成功。

ALTER TABLE `yshop_hotpot_session_dish`
  ADD COLUMN `served_qty` int NOT NULL DEFAULT '0' COMMENT '已上桌份数。不减少原始 quantity' AFTER `quantity`,
  ADD COLUMN `refunded_qty` int NOT NULL DEFAULT '0' COMMENT '未上桌已退份数。已上桌的不能退' AFTER `served_qty`;

ALTER TABLE `yshop_hotpot_session_dish`
  ADD CONSTRAINT `chk_hotpot_dish_served_refunded` CHECK (`served_qty` >= 0 AND `refunded_qty` >= 0 AND `served_qty` + `refunded_qty` <= `quantity`);

ALTER TABLE `yshop_hotpot_session`
  ADD COLUMN `fees_waived` tinyint NOT NULL DEFAULT '0' COMMENT '1 表示锅底费不再计入应付。不删除锅底行' AFTER `zero_pay`;
