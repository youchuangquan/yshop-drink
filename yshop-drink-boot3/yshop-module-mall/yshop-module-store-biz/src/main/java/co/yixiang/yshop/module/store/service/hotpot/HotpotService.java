package co.yixiang.yshop.module.store.service.hotpot;

import co.yixiang.yshop.framework.common.hotpot.HotpotFlow;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotBoundBillResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotStaffHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotPotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;

import java.util.List;

public interface HotpotService {

    HotpotSlotResp createSlot(long shopId, long actorUid, int capacity, String startTime, String endTime,
                              String requestKey);

    HotpotTableResp createTable(long shopId, long actorUid, String signCode, String requestKey);

    HotpotPotResp createPot(long shopId, long actorUid, String name, Integer priceFen, String requestKey);

    List<HotpotSlotResp> listSlots(long shopId);

    List<HotpotStaffHoldResp> listCurrentHolds(long shopId);

    HotpotBoundBillResp boundBill(long shopId, String signCode, long callerUid);

    List<HotpotTableResp> listTables(long shopId);

    List<HotpotPotResp> listPots(long shopId);

    HotpotHoldResp reserve(long shopId, long ownerUid, long slotId, String requestKey);

    HotpotHoldResp cancel(long actorUid, long holdId);

    HotpotHoldResp seat(long shopId, long holdId, long slotId, long actorUid, boolean staff, String requestKey);

    HotpotHoldResp walkIn(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey);

    HotpotSessionResp open(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey);

    /**
     * 顾客点一道菜。桌是空的时，这一笔才占桌并开台。
     */
    HotpotSessionResp orderDish(long shopId, long slotId, String signCode, long actorUid, long productId,
                                int quantity, Integer version, String requestKey);

    /**
     * 店员把预约人的占位安排到空桌，并开出属于该预约人的台次。
     */
    HotpotSessionResp seatReservation(long shopId, long holdId, long slotId, String signCode, String requestKey);

    HotpotSessionResp addPot(long shopId, long sessionId, long potId, int version, long actorUid, boolean staff,
                             String requestKey);

    /**
     * 本店进行中的台次。店从登录的店员来，不从顾客请求来。
     */
    List<HotpotSessionResp> listOpenSessions(long shopId);

    /**
     * 店员登记上菜。调用方必须已经确认是这家店的店员。
     */
    HotpotSessionResp serve(long shopId, long sessionId, int version, String requestKey,
                            List<HotpotFlow.LineQty> items);

    /**
     * 店员退还未上桌的份数。顾客不能走这里。全退且应付为 0 时在同一次里释放桌台。
     */
    HotpotSessionResp refund(long shopId, long sessionId, int version, String requestKey,
                             List<HotpotFlow.LineQty> items);

    HotpotSessionResp settle(long actorUid, long sessionId, int version, boolean staff, Long staffShop);

    HotpotScanResp scan(long shopId, String signCode);

}
