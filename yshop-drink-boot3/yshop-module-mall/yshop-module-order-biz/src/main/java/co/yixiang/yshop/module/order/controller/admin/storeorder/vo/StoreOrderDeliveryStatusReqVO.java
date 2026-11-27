package co.yixiang.yshop.module.order.controller.admin.storeorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 外卖配送状态更新 Request VO")
@Data
public class StoreOrderDeliveryStatusReqVO {

    @Schema(description = "订单ID", required = true, example = "31716")
    @NotNull(message = "订单ID不能为空")
    private Long id;

    @Schema(description = "目标状态，只允许 1 待收货或 2 已收货", required = true, example = "1")
    @NotNull(message = "订单状态不能为空")
    private Integer status;

    @Schema(description = "门店编号。以登录店员的门店为准，与登录门店不一致时拒绝")
    private Long shopId;

    @Schema(description = "客户端稳定请求键。同一订单、同一目标重放时不再写操作记录")
    private String requestKey;

}
