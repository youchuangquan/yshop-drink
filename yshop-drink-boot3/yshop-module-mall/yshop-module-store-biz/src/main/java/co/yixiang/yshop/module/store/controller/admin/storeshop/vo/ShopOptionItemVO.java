package co.yixiang.yshop.module.store.controller.admin.storeshop.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShopOptionItemVO {

    @Schema(description = "选项 id")
    private Long id;

    @Schema(description = "FLAVOR 口味，ADDON 加料")
    private String kind;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "本店配置的价格。不填不能保存")
    private BigDecimal price;

}
