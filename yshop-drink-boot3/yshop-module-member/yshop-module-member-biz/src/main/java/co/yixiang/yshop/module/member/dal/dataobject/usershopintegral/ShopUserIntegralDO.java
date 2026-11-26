package co.yixiang.yshop.module.member.dal.dataobject.usershopintegral;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 用户在单店的积分。余额只按 uid + shopId 读写。
 */
@TableName("yshop_user_shop_integral")
@KeySequence("yshop_user_shop_integral_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopUserIntegralDO extends BaseDO {

    @TableId
    private Long id;

    private Long uid;

    private Long shopId;

    private BigDecimal integral;

}
