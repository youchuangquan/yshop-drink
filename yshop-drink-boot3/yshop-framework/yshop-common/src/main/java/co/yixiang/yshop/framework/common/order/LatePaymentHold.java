package co.yixiang.yshop.framework.common.order;

/**
 * 晚到支付的处置口径（D1）还没定。
 * 实现只能把通知挂起：不能自动取消、不能自动完成、不能自己加一个超时。
 */
@FunctionalInterface
public interface LatePaymentHold {

    void latePayment(String orderId, String notifyKey, String payType);

}
