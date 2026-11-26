package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_session")
@KeySequence("yshop_hotpot_session_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotSessionDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private Long slotId;

    private Long tableId;

    private Long holdId;

    private Long ownerUid;

    private Integer version;

    private Integer payableFen;

    private Integer zeroPay;

    private String status;

    private String requestKey;

}
