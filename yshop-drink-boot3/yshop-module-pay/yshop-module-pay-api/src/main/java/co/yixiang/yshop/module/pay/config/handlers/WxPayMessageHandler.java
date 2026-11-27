package co.yixiang.yshop.module.pay.config.handlers;

import co.yixiang.yshop.module.pay.mq.producer.PayNoticeProducer;
import com.egzosn.pay.common.api.PayMessageHandler;
import com.egzosn.pay.common.api.PayService;
import com.egzosn.pay.common.bean.PayOutMessage;
import com.egzosn.pay.common.exception.PayErrorException;
import com.egzosn.pay.wx.bean.WxPayMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 微信支付回调处理器
 * @author hupeng
 * @date 2023/7/15
 */
@Component
@Slf4j
public class WxPayMessageHandler implements PayMessageHandler<WxPayMessage, PayService> {

    @Resource
    private PayNoticeProducer payNoticeProducer;

    @Override
    public PayOutMessage handle(WxPayMessage payMessage, Map<String, Object> context, PayService payService) throws PayErrorException {

        log.info("======pay notice ========");

        //交易状态
        if ("SUCCESS".equals(payMessage.getPayMessage().get("result_code"))){
            String orderId = text(payMessage.getPayMessage().get("out_trade_no"));
            String notifyKey = text(payMessage.getPayMessage().get("transaction_id"));
            // 微信未接通。记下通知号后仍返回失败，入账时也不会把订单标成已付。
            if (orderId == null || notifyKey == null) {
                log.warn("微信支付通知缺少订单号或通知键，拒绝入账");
                return payService.getPayOutMessage("FAIL", "失败");
            }
            payNoticeProducer.sendPayNoticeMessage(orderId, "weixin", notifyKey);
            return payService.getPayOutMessage("FAIL", "失败");
        }

        return  payService.getPayOutMessage("FAIL", "失败");
    }

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }
}
