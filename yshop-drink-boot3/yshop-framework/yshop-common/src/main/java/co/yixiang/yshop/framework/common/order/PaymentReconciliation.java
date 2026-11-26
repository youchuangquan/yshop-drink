package co.yixiang.yshop.framework.common.order;

/**
 * 支付通知入账。同一通知键重放不会再次付款，也不会再次完成。
 * 未接通的渠道直接拒绝，不改已付状态。
 * 订单已经不在待支付（取消、退款、完成、或不存在）时挂起，晚到处置未定。
 */
public final class PaymentReconciliation {

    private PaymentReconciliation() {
    }

    public enum Disposition {
        APPLIED,
        REPLAY,
        UNCONNECTED,
        LATE_HELD
    }

    public static final class OrderState {
        public final boolean payable;
        public final boolean paid;
        public final boolean completed;

        public OrderState(boolean payable, boolean paid, boolean completed) {
            this.payable = payable;
            this.paid = paid;
            this.completed = completed;
        }
    }

    public static final class Result {
        private final Disposition disposition;
        private final boolean settled;

        public Result(Disposition disposition, boolean settled) {
            this.disposition = disposition;
            this.settled = settled;
        }

        public Disposition disposition() {
            return disposition;
        }

        public boolean settled() {
            return settled;
        }
    }

    public interface Orders {
        OrderState load(String orderId);

        /**
         * @return 这一次是否把未支付改成了已支付
         */
        boolean markPaidIfUnpaid(String orderId, String payType);
    }

    public interface Notices {
        Disposition find(String notifyKey);

        /**
         * @return false 表示这个键已经有记录。数据库实现也可以抛重复键异常，调用方不得再入账。
         */
        boolean claim(String notifyKey, Disposition disposition);

        void replace(String notifyKey, Disposition disposition);
    }

    public static Result apply(String notifyKey, String payType, String orderId,
                               Orders orders, Notices notices, LatePaymentHold hold,
                               Runnable completeOnce) {
        String key = OrderRequestKeys.requireNotify(notifyKey);
        Disposition prior = notices.find(key);
        if (prior != null) {
            Disposition shown = prior == Disposition.APPLIED ? Disposition.REPLAY : prior;
            return new Result(shown, false);
        }
        if (!PayChannels.settles(payType)) {
            notices.claim(key, Disposition.UNCONNECTED);
            return new Result(Disposition.UNCONNECTED, false);
        }
        OrderState state = orders.load(orderId);
        if (state != null && (state.paid || state.completed)) {
            notices.claim(key, Disposition.REPLAY);
            return new Result(Disposition.REPLAY, false);
        }
        if (state == null || !state.payable) {
            notices.claim(key, Disposition.LATE_HELD);
            hold.latePayment(orderId, key, payType);
            return new Result(Disposition.LATE_HELD, false);
        }
        if (!notices.claim(key, Disposition.APPLIED)) {
            return new Result(Disposition.REPLAY, false);
        }
        if (!orders.markPaidIfUnpaid(orderId, payType)) {
            OrderState again = orders.load(orderId);
            if (again != null && (again.paid || again.completed)) {
                notices.replace(key, Disposition.REPLAY);
                return new Result(Disposition.REPLAY, false);
            }
            notices.replace(key, Disposition.LATE_HELD);
            hold.latePayment(orderId, key, payType);
            return new Result(Disposition.LATE_HELD, false);
        }
        completeOnce.run();
        return new Result(Disposition.APPLIED, true);
    }

}
