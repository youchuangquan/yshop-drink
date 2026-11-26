package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_session_pot")
@KeySequence("yshop_hotpot_session_pot_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotSessionPotDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private Long sessionId;

    private Long potId;

    private String name;

    private Integer priceFen;

}
