package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

/**
 * 店员看到的当前占位。只有这六项。
 */
@Data
public class HotpotStaffHoldResp {

    private Long id;

    private Long slotId;

    private Long shopId;

    private Long uid;

    private String status;

    private Long sessionId;

}
