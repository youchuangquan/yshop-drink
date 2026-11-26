package co.yixiang.yshop.module.store.dal.dataobject.shopoption;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 门店口味或加料。价格必须来自本店配置。
 */
@TableName("yshop_shop_option")
@KeySequence("yshop_shop_option_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopOptionDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    /**
     * FLAVOR 或 ADDON
     */
    private String kind;

    private String name;

    private BigDecimal price;

}
