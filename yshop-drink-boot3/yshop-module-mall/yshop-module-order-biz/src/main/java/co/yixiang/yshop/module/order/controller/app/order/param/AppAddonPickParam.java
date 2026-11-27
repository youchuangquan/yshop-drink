package co.yixiang.yshop.module.order.controller.app.order.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 顾客选中的加料。只传选项和数量，价格由服务端按本店配置计算。
 */
@Data
public class AppAddonPickParam {

    @Schema(description = "加料选项 id")
    private Long optionId;

    @Schema(description = "数量")
    private Integer number;

}
