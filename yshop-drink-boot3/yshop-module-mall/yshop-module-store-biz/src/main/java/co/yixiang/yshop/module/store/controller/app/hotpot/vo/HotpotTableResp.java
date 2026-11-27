package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

/**
 * 店员看桌台。不返回绑定的顾客。
 */
@Data
public class HotpotTableResp {

    private Long tableId;

    private Long shopId;

    private String signCode;

    private Boolean occupied;

}
