package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 门店锅底。价格必须来自这里，空列表不补默认价。
 */
@TableName("yshop_shop_pot")
@KeySequence("yshop_shop_pot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopPotDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private String name;

    private Integer priceFen;

}
