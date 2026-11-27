package co.yixiang.yshop.module.store.dal.mysql.hotpot;

import co.yixiang.yshop.framework.mybatis.core.mapper.BaseMapperX;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HotpotSessionMapper extends BaseMapperX<HotpotSessionDO> {

    @Select("select id, shop_id, slot_id, table_id, hold_id, owner_uid, version, payable_fen, zero_pay, fees_waived, status, request_key from yshop_hotpot_session where deleted = 0 and shop_id = #{shopId}")
    List<HotpotSessionDO> selectByShop(@Param("shopId") Long shopId);

    @Select("select id, shop_id, slot_id, table_id, hold_id, owner_uid, version, payable_fen, zero_pay, fees_waived, status, request_key from yshop_hotpot_session where deleted = 0 and id = #{id}")
    HotpotSessionDO selectBySessionId(@Param("id") Long id);

    @Update("update yshop_hotpot_session set table_id = #{tableId}, hold_id = #{holdId}, version = #{version}, payable_fen = #{payableFen}, zero_pay = #{zeroPay}, fees_waived = #{feesWaived}, status = #{status} where id = #{id} and shop_id = #{shopId} and deleted = 0")
    int updateSession(@Param("id") Long id, @Param("shopId") Long shopId, @Param("tableId") Long tableId,
                      @Param("holdId") Long holdId, @Param("version") int version, @Param("payableFen") int payableFen,
                      @Param("zeroPay") int zeroPay, @Param("feesWaived") int feesWaived, @Param("status") String status);

}
