package co.yixiang.yshop.module.order.service.storeorder;

import co.yixiang.yshop.framework.common.order.LatePaymentHold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 晚到支付处置未定。只记录，不取消订单，不把订单改成完成，也不起一个新的超时任务。
 */
@Component
@Slf4j
public class UnsetLatePaymentHold implements LatePaymentHold {

    @Override
    public void latePayment(String orderId, String notifyKey, String payType) {
        log.warn("晚到支付暂不处置：订单 {} 通知 {} 渠道 {}。不自动取消、不自动完成、不设超时。",
                orderId, notifyKey, payType);
    }

}
