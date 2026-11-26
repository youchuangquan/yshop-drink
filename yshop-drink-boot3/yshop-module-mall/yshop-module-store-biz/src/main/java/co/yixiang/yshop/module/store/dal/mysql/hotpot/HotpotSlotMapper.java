package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSlotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotSlotMapper extends BaseMapperX<HotpotSlotDO> {

    @Select("select id, shop_id, capacity, occupied, start_time, end_time from yshop_hotpot_slot where deleted = 0 and shop_id = #{shopId}")
    List<HotpotSlotDO> selectByShop(@Param("shopId") Long shopId);

    @Select("select shop_id from yshop_hotpot_slot where deleted = 0 and id = #{id}")
    Long selectShopId(@Param("id") Long id);

    @Update("update yshop_hotpot_slot set occupied = occupied + #{delta} where id = #{id} and shop_id = #{shopId} and deleted = 0 and occupied + #{delta} <= capacity and occupied + #{delta} >= 0")
    int addOccupied(@Param("id") Long id, @Param("shopId") Long shopId, @Param("delta") int delta);

}
