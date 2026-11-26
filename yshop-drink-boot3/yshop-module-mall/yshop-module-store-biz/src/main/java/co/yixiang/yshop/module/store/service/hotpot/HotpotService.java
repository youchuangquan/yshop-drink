package co.yixiang.yshop.module.store.service.hotpot;

import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotPotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;

import java.util.List;

public interface HotpotService {

    HotpotSlotResp createSlot(long shopId, long actorUid, int capacity, String requestKey);

    HotpotTableResp createTable(long shopId, long actorUid, String signCode, String requestKey);

    HotpotPotResp createPot(long shopId, long actorUid, String name, Integer priceFen, String requestKey);

    List<HotpotSlotResp> listSlots(long shopId);

    List<HotpotTableResp> listTables(long shopId);

    List<HotpotPotResp> listPots(long shopId);

    HotpotHoldResp reserve(long shopId, long ownerUid, long slotId, String requestKey);

    HotpotHoldResp cancel(long actorUid, long holdId);

    HotpotHoldResp seat(long shopId, long holdId, long slotId, long actorUid, boolean staff, String requestKey);

    HotpotHoldResp walkIn(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey);

    HotpotSessionResp open(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey);

    HotpotSessionResp addPot(long shopId, long sessionId, long potId, int version, long actorUid, boolean staff,
                             String requestKey);

    HotpotSessionResp settle(long actorUid, long sessionId, int version, boolean staff, Long staffShop);

    HotpotScanResp scan(long shopId, String signCode);

}
