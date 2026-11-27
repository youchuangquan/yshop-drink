package co.yixiang.yshop.module.order.service.storeorder;

import co.yixiang.yshop.framework.common.enums.OrderInfoEnum;
import co.yixiang.yshop.framework.common.order.OrderRequestKeys;
import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.module.order.enums.OrderLogEnum;

import static co.yixiang.yshop.module.order.enums.ErrorCodeConstants.ORDER_STATUS_ERROR;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 外卖履约进度只认订单 status。合法迁移是 0→1 和 1→2。
 * 同一个请求键再打到同一个目标上是重放，不再写状态，也不再写操作记录。
 */
public final class TakeoutDeliveryStatus {

    public static final String ORDER_TYPE = OrderLogEnum.ORDER_TAKE_OUT.getValue();
    public static final String CHANGE_TYPE = "delivery_status";
    private static final String KEY_MARK = " key=";

    private TakeoutDeliveryStatus() {
    }

    public enum Effect {
        APPLY,
        REPLAY
    }

    public static final class Snapshot {
        public final Long id;
        public final Long shopId;
        public final Integer paid;
        public final String orderType;
        public final Integer status;
        public final Integer refundStatus;

        public Snapshot(Long id, Long shopId, Integer paid, String orderType, Integer status, Integer refundStatus) {
            this.id = id;
            this.shopId = shopId;
            this.paid = paid;
            this.orderType = orderType;
            this.status = status;
            this.refundStatus = refundStatus;
        }
    }

    public static final class Stamp {
        public final long orderId;
        public final int target;
        public final String requestKey;

        public Stamp(long orderId, int target, String requestKey) {
            this.orderId = orderId;
            this.target = target;
            this.requestKey = requestKey;
        }
    }

    public static final class Move {
        private final Effect effect;
        private final int from;
        private final int to;
        private final String requestKey;

        public Move(Effect effect, int from, int to, String requestKey) {
            this.effect = effect;
            this.from = from;
            this.to = to;
            this.requestKey = requestKey;
        }

        public Effect effect() {
            return effect;
        }

        public int from() {
            return from;
        }

        public int to() {
            return to;
        }

        public String requestKey() {
            return requestKey;
        }
    }

    public static boolean legacyWriteBlocked(String orderType) {
        return ORDER_TYPE.equals(orderType);
    }

    /**
     * 店员门店必须已经由登录态决定。请求体里的门店号不能换成订单上的门店。
     * 重放成功时调用方不得再更新状态，也不得再插入操作记录。
     */
    public static Move decide(Snapshot order, long staffShopId, int target, String rawKey, Stamp stamp) {
        if (order == null || order.id == null) {
            throw exception(ORDER_STATUS_ERROR);
        }
        ShopAccess.assertSameShop(order.shopId, staffShopId);
        String key = OrderRequestKeys.legacy(rawKey);
        if (stamp != null) {
            if (key == null || stamp.orderId != order.id || !key.equals(stamp.requestKey) || stamp.target != target) {
                throw exception(ORDER_STATUS_ERROR);
            }
            if (order.status != null && order.status == target) {
                return new Move(Effect.REPLAY, order.status, target, key);
            }
            throw exception(ORDER_STATUS_ERROR);
        }
        if (!ORDER_TYPE.equals(order.orderType)
                || !OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.paid)
                || !OrderInfoEnum.REFUND_STATUS_0.getValue().equals(order.refundStatus)
                || order.status == null
                || !allowedTarget(target)
                || !legal(order.status, target)) {
            throw exception(ORDER_STATUS_ERROR);
        }
        return new Move(Effect.APPLY, order.status, target, key);
    }

    public static String operationMessage(int target, String requestKey) {
        String desc = target == OrderInfoEnum.STATUS_1.getValue()
                ? OrderInfoEnum.STATUS_1.getDesc()
                : OrderInfoEnum.STATUS_2.getDesc();
        if (requestKey == null) {
            return desc;
        }
        return desc + KEY_MARK + requestKey;
    }

    public static String keyOf(String message) {
        if (message == null) {
            return null;
        }
        int at = message.indexOf(KEY_MARK);
        if (at < 0) {
            return null;
        }
        String key = message.substring(at + KEY_MARK.length()).trim();
        return key.isEmpty() ? null : key;
    }

    public static Integer targetOf(String message) {
        if (message == null) {
            return null;
        }
        if (message.startsWith(OrderInfoEnum.STATUS_1.getDesc())) {
            return OrderInfoEnum.STATUS_1.getValue();
        }
        if (message.startsWith(OrderInfoEnum.STATUS_2.getDesc())) {
            return OrderInfoEnum.STATUS_2.getValue();
        }
        return null;
    }

    private static boolean allowedTarget(int target) {
        return target == OrderInfoEnum.STATUS_1.getValue() || target == OrderInfoEnum.STATUS_2.getValue();
    }

    private static boolean legal(int from, int to) {
        return (from == OrderInfoEnum.STATUS_0.getValue() && to == OrderInfoEnum.STATUS_1.getValue())
                || (from == OrderInfoEnum.STATUS_1.getValue() && to == OrderInfoEnum.STATUS_2.getValue());
    }

}
