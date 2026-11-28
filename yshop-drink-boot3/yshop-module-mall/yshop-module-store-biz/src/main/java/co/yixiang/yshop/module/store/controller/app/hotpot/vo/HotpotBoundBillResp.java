package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

import java.util.List;

/**
 * 绑定顾客自己的开台账单。金额和明细来自开台，不在客户端另编。
 * 没有顾客号，也没有实付金额。
 */
@Data
public class HotpotBoundBillResp {

    private Long sessionId;

    private Integer version;

    private Integer payableFen;

    private String status;

    private Boolean paid;

    private Boolean zeroPayReleased;

    private Boolean feesWaived;

    private List<PotLine> pots;

    private List<DishLine> dishes;

    @Data
    public static class PotLine {

        private Long potId;

        private String name;

        private Integer priceFen;

    }

    @Data
    public static class DishLine {

        private Long lineId;

        private Long productId;

        private String name;

        private Integer priceFen;

        private Integer quantity;

        private Integer servedQty;

        private Integer refundedQty;

        private Integer refundableQty;

    }

}
