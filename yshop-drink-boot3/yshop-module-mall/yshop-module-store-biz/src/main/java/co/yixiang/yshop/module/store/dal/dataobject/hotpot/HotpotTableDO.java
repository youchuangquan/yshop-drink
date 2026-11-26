package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("yshop_hotpot_table")
@KeySequence("yshop_hotpot_table_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HotpotTableDO extends BaseDO {

    @TableId
    private Long id;

    private Long shopId;

    private String signCode;

    private Integer occupied;

    private Long boundUid;

    private Long sessionId;

}
