package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_request")
@KeySequence("yshop_hotpot_request_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotRequestDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private String requestKey;

    private Long ownerUid;

    private Long slotId;

    private String action;

    private Long holdId;

    private Long sessionId;

    private Long potId;

    private Long resourceId;

    private String mark;

}
