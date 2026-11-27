package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotHoldDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotHoldMapper extends BaseMapperX<HotpotHoldDO> {

    @Select("select id, shop_id, slot_id, owner_uid, request_key, status from yshop_hotpot_hold where deleted = 0 and shop_id = #{shopId}")
    List<HotpotHoldDO> selectByShop(@Param("shopId") Long shopId);

    @Select("select id, shop_id, slot_id, owner_uid, request_key, status from yshop_hotpot_hold where deleted = 0 and id = #{id}")
    HotpotHoldDO selectByHoldId(@Param("id") Long id);

    @Update("update yshop_hotpot_hold set status = #{status} where id = #{id} and shop_id = #{shopId} and deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("shopId") Long shopId, @Param("status") String status);

}
