package co.yixiang.yshop.module.pay.config.handlers;

import co.yixiang.yshop.module.pay.mq.producer.PayNoticeProducer;
import com.egzosn.pay.ali.api.AliPayService;
import com.egzosn.pay.ali.bean.AliPayMessage;
import com.egzosn.pay.common.api.PayMessageHandler;
import com.egzosn.pay.common.bean.PayOutMessage;
import com.egzosn.pay.common.exception.PayErrorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 支付宝支付回调处理器
 * @author hupeng
 * @date 2023/7/15
 */
@Component
@Slf4j
public class AliPayMessageHandler implements PayMessageHandler<AliPayMessage, AliPayService> {

    @Resource
    private PayNoticeProducer payNoticeProducer;
    /**
     * 处理支付回调消息的处理器接口
     *
     * @param payMessage 支付消息
     * @param context    上下文，如果handler或interceptor之间有信息要传递，可以用这个
     * @param payService 支付服务
     * @return xml, text格式的消息，如果在异步规则里处理的话，可以返回null
     * @throws PayErrorException 支付错误异常
     */
    @Override
    public PayOutMessage handle(AliPayMessage payMessage, Map<String, Object> context, AliPayService payService) throws PayErrorException {

        Map<String, Object> message = payMessage.getPayMessage();
        //交易状态
        String trade_status = (String) message.get("trade_status");

        //交易完成
        if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)) {

            String orderId = text(payMessage.getPayMessage().get("out_trade_no"));
            String notifyKey = text(payMessage.getPayMessage().get("trade_no"));
            // 支付宝未接通。记下通知号后仍返回失败，入账时也不会把订单标成已付。
            if (orderId == null || notifyKey == null) {
                log.warn("支付宝通知缺少订单号或通知键，拒绝入账");
                return payService.getPayOutMessage("fail", "失败");
            }
            payNoticeProducer.sendPayNoticeMessage(orderId, "alipay", notifyKey);
            return payService.getPayOutMessage("fail", "失败");

        }

        return payService.getPayOutMessage("fail", "失败");
    }

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }
}
