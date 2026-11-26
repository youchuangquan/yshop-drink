package co.yixiang.yshop.module.store.dal.dataobject.hotpot;

import co.yixiang.yshop.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

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

    /**
     * 时段开始。空表示旧数据没有时刻。不用于自动取消。
     */
    private LocalTime startTime;

    /**
     * 时段结束。空表示旧数据没有时刻。过点不释放容量。
     */
    private LocalTime endTime;

}
