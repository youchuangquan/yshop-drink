package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开台上的菜。名称和价格是下单时从本店商品抄下来的。
 */
@TableName("yshop_hotpot_session_dish")
@KeySequence("yshop_hotpot_session_dish_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotSessionDishDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private Long sessionId;

    private Long productId;

    private String name;

    private Integer priceFen;

    private Integer quantity;

    private Integer servedQty;

    private Integer refundedQty;

}
