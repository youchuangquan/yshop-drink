package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotTableDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotTableMapper extends BaseMapperX<HotpotTableDO> {

    @Select("select id, shop_id, sign_code, occupied, bound_uid, session_id from yshop_hotpot_table where deleted = 0 and shop_id = #{shopId}")
    List<HotpotTableDO> selectByShop(@Param("shopId") Long shopId);

    @Select("select sign_code, occupied from yshop_hotpot_table where deleted = 0 and shop_id = #{shopId} and sign_code = #{signCode}")
    HotpotTableDO selectPublic(@Param("shopId") Long shopId, @Param("signCode") String signCode);

    @Update("update yshop_hotpot_table set occupied = #{occupied}, bound_uid = #{boundUid}, session_id = #{sessionId} where id = #{id} and shop_id = #{shopId} and deleted = 0")
    int updateOccupancy(@Param("id") Long id, @Param("shopId") Long shopId, @Param("occupied") int occupied,
                        @Param("boundUid") Long boundUid, @Param("sessionId") Long sessionId);

}
