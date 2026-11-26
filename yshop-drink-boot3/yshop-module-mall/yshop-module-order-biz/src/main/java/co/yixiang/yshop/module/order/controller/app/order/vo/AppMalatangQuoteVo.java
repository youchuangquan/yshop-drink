package co.yixiang.yshop.module.order.controller.app.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 服务端报价。没有配置的费用保持 null，不返回 0 来冒充。
 */
@Data
public class AppMalatangQuoteVo {

    private BigDecimal goodsAmount;

    private Long flavorOptionId;

    private String flavorName;

    private BigDecimal flavorAmount;

    private BigDecimal addonAmount;

    private List<AppOrderOptionVo> addons;

    private BigDecimal packingAmount;

    private BigDecimal deliveryAmount;

    private BigDecimal couponAmount;

    private BigDecimal payAmount;

}
