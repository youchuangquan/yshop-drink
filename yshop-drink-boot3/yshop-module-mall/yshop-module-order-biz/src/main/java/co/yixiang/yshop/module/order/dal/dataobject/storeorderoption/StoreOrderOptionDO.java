package co.yixiang.yshop.module.order.dal.dataobject.storeorderoption;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单上的口味或加料。单价是下单时这家店的配置价。
 */
@TableName("yshop_store_order_option")
@KeySequence("yshop_store_order_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreOrderOptionDO extends BaseDO {

    @TableId
    private Long id;

    private String orderId;

    private Long shopId;

    private Long optionId;

    private String kind;

    private String name;

    private BigDecimal unitPrice;

    private Integer number;

    private BigDecimal amount;

}
