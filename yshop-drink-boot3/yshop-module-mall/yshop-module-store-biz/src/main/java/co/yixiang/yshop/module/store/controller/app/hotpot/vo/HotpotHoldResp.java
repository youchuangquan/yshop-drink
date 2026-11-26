package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

/**
 * 占位结果。没有顾客姓名、手机号，也没有桌号。
 */
@Data
public class HotpotHoldResp {

    private Long holdId;

    private Long shopId;

    private Long slotId;

    private String status;

    private String requestKey;

    /**
     * 始终为 false。没有到店保留时长，也不会自动取消。
     */
    private Boolean lateAutoCancels;

}
