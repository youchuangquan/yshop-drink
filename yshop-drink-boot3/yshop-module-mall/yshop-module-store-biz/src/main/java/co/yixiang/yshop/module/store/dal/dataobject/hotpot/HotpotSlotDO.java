package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_slot")
@KeySequence("yshop_hotpot_slot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotSlotDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private Integer capacity;

    private Integer occupied;

}
