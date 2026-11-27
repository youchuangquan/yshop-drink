package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

import java.util.List;

/**
 * 开台结果。paid 只会被写成 false：这一轮没有接通的收款渠道。
 */
@Data
public class HotpotSessionResp {

    private Long sessionId;

    private Long shopId;

    private Long slotId;

    private Long holdId;

    private Long ownerUid;

    private Integer version;

    private Integer payableFen;

    private String status;

    private Boolean paid;

    private Boolean zeroPayReleased;

    private Boolean paymentSuccess;

    private String disposition;

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

        private Long productId;

        private String name;

        private Integer priceFen;

        private Integer quantity;

    }

}
