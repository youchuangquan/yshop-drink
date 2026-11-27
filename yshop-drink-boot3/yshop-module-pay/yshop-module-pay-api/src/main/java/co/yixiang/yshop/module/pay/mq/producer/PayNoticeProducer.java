package co.yixiang.yshop.module.pay.mq.producer;

import co.yixiang.yshop.framework.mq.redis.core.RedisMQTemplate;
import co.yixiang.yshop.module.pay.mq.message.PayNoticeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class PayNoticeProducer {
    @Resource
    private RedisMQTemplate redisMQTemplate;

    /**
     * 发送消息
     * @param orderId 订单编号
     */
    public void sendPayNoticeMessage(String orderId, String payType, String notifyKey) {
        PayNoticeMessage payNoticeMessage = new PayNoticeMessage()
                .setOrderId(orderId)
                .setPayType(payType)
                .setNotifyKey(notifyKey);
        redisMQTemplate.send(payNoticeMessage);
    }
}
