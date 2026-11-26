package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 从本店商品读出的报价。不是一张新表，也不在这里改价。
 */
@Data
public class HotpotMenuDishRow {

    private Long id;

    private Long shopId;

    private String storeName;

    private BigDecimal price;

    private Integer isShow;

}
