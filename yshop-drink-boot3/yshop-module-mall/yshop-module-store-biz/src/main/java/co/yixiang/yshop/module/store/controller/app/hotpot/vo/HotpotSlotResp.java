package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

@Data
public class HotpotSlotResp {

    private Long slotId;

    private Long shopId;

    private Integer capacity;

    private Integer occupied;

    /**
     * 当天时刻，HH:mm:ss。空表示这条时段还没有起止。
     */
    private String startTime;

    /**
     * 当天时刻，HH:mm:ss。过点与否由店员自己看，服务端不因此取消预约。
     */
    private String endTime;

}
