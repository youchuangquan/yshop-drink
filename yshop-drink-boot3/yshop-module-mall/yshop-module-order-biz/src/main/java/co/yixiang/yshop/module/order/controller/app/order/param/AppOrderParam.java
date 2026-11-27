package co.yixiang.yshop.module.order.controller.app.order.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * @ClassName OrderParam
 * @Author hupeng <610796224@qq.com>
 * @Date 2023/8/22
 **/
@Data
public class AppOrderParam implements Serializable {

    @Schema(description = "地址ID", required = true)
    private String addressId;

    @Schema(description = "门店ID", required = true)
    private String shopId;

    @Schema(description = "手机号", required = true)
    private String mobile;

    @Schema(description = "优惠券ID", required = true)
    private String couponId;

    @Schema(description = "购买类型:takein=自取,takeout=外卖,desk=扫码点餐", required = true)
    private String orderType;

    @Size(max = 200,message = "长度超过了限制")
    @Schema(description = "备注", required = true)
    private String remark;

    @Schema(description = "取餐时间", required = true)
    private Integer gettime;

    @Schema(description = "秒杀产品ID", required = true)
    private List<String> productId;

    @Schema(description = "规格", required = true)
    private List<String> spec;

    @Schema(description = "数量", required = true)
    private List<String>  number;

    @Schema(description = "口味选项 id。不传表示不选。价格由服务端读取，请求里的价格无效")
    private Long flavorOptionId;

    @Schema(description = "加料。只认选项 id 和数量")
    private List<AppAddonPickParam> addons;

    @Schema(description = "支付类型", required = true)
    private String payType;

    @Schema(description = "桌面ID", required = true)
    private Long deskId;

    @Schema(description = "桌号", required = true)
    private String deskNumber;

    @Schema(description = "就餐人数", required = true)
    private Integer deskPeople;

    @Schema(description = "订单号", required = true)
    private String orderId;

    @Size(max = 64, message = "长度超过了限制")
    @Schema(description = "稳定请求键。同一个键重复提交返回已有订单。不传仍可下单，但不是防重；空字符串会拒绝")
    private String requestKey;

}
