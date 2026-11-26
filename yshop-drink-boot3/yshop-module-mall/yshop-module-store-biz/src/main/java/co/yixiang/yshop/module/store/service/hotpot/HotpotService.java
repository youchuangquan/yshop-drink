package co.yixiang.yshop.module.store.service.hotpot;

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

    HotpotSessionResp settle(long actorUid, long sessionId, int version, boolean staff, Long staffShop);

    HotpotScanResp scan(long shopId, String signCode);

}
