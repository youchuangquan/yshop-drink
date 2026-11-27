package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotRequestMapper extends BaseMapperX<HotpotRequestDO> {

    @Select("select id, shop_id, request_key, owner_uid, slot_id, action, hold_id, session_id, pot_id, resource_id, mark from yshop_hotpot_request where deleted = 0 and shop_id = #{shopId}")
    List<HotpotRequestDO> selectByShop(@Param("shopId") Long shopId);

    @Update("update yshop_hotpot_request set hold_id = #{holdId}, session_id = #{sessionId}, pot_id = #{potId}, mark = #{mark} where id = #{id} and shop_id = #{shopId} and deleted = 0")
    int updateLinks(@Param("id") Long id, @Param("shopId") Long shopId, @Param("holdId") Long holdId,
                    @Param("sessionId") Long sessionId, @Param("potId") Long potId, @Param("mark") String mark);

}
