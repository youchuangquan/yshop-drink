package co.yixiang.yshop.module.order.controller.app.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppOrderOptionVo {

    private String kind;

    private String name;

    private BigDecimal unitPrice;

    private Integer number;

    private BigDecimal amount;

}
