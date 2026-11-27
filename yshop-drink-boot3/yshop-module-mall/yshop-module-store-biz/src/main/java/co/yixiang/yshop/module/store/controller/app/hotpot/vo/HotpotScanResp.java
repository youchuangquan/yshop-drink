package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import lombok.Data;

/**
 * 公开扫码。只有签码和是否占用。
 */
@Data
public class HotpotScanResp {

    private String signCode;

    private Boolean occupied;

}
