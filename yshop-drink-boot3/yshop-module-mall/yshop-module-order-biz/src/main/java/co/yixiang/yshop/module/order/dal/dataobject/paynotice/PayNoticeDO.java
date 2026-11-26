package co.yixiang.yshop.module.order.dal.dataobject.paynotice;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支付通知入账记录。同一 notifyKey 只处理一次。
 */
@TableName("yshop_pay_notice")
@KeySequence("yshop_pay_notice_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PayNoticeDO extends BaseDO {

    @TableId
    private Long id;

    private String notifyKey;

    private String orderId;

    private String payType;

    /**
     * APPLIED、REPLAY、UNCONNECTED、LATE_HELD
     */
    private String disposition;

}
