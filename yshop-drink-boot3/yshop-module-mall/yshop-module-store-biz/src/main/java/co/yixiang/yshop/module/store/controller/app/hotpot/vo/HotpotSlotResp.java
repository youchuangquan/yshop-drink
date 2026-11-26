package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

@Data
public class HotpotSlotResp {

    private Long slotId;

    private Long shopId;

    private Integer capacity;

    private Integer occupied;

}
