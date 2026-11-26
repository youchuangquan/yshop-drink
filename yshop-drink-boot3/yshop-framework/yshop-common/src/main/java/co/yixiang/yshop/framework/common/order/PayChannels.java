package co.yixiang.yshop.framework.common.order;

/**
 * 这一轮没有接通微信或支付宝。这两个渠道不能把订单标成已付。
 * 余额和现金是本系统里已经发生的结算，可以入账一次。
 */
public final class PayChannels {

    private PayChannels() {
    }

    public static boolean settles(String payType) {
        return "yue".equals(payType) || "cash".equals(payType);
    }

    /**
     * 余额、现金没有渠道通知号，用支付方式和订单号拼一个稳定键。
     * 微信、支付宝不在这里编通知号。
     */
    public static String localNotifyKey(String payType, String orderId) {
        if (!settles(payType) || orderId == null || orderId.trim().isEmpty()) {
            return null;
        }
        return payType + "-" + orderId.trim();
    }

}
