package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_hold")
@KeySequence("yshop_hotpot_hold_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotHoldDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private Long slotId;

    private Long ownerUid;

    private String requestKey;

    private String status;

}
