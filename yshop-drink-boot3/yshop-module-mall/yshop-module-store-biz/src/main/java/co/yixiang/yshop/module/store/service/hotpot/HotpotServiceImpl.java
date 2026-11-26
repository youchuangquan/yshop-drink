package co.yixiang.yshop.module.store.service.hotpot;

import co.yixiang.yshop.framework.common.hotpot.HotpotFlow;
import co.yixiang.yshop.framework.common.order.OrderRequestGate;
import co.yixiang.yshop.framework.common.shop.ShopAccess;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotBoundBillResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotStaffHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotPotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotHoldDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotRequestDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionPotDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSlotDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotTableDO;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.ShopPotDO;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotHoldMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotRequestMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotSessionMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotSessionPotMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotSlotMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.HotpotTableMapper;
import co.yixiang.yshop.module.store.dal.mysql.hotpot.ShopPotMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.NOT_FOUND;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class HotpotServiceImpl implements HotpotService {

    @Resource
    private HotpotSlotMapper slotMapper;
    @Resource
    private HotpotTableMapper tableMapper;
    @Resource
    private HotpotHoldMapper holdMapper;
    @Resource
    private HotpotSessionMapper sessionMapper;
    @Resource
    private HotpotSessionPotMapper sessionPotMapper;
    @Resource
    private ShopPotMapper potMapper;
    @Resource
    private HotpotRequestMapper requestMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public HotpotSlotResp createSlot(long shopId, long actorUid, int capacity, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotFlow.Slot slot = write(shop, requestKey,
                (board, key) -> HotpotFlow.addSlot(board, shop, capacity, actorUid, key));
        return toSlot(slot);
    }

    @Override
    public HotpotTableResp createTable(long shopId, long actorUid, String signCode, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotFlow.Table table = write(shop, requestKey,
                (board, key) -> HotpotFlow.addTable(board, shop, signCode, actorUid, key));
        return toTable(table);
    }

    @Override
    public HotpotPotResp createPot(long shopId, long actorUid, String name, Integer priceFen, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotFlow.Pot pot = write(shop, requestKey,
                (board, key) -> HotpotFlow.addPotConfig(board, shop, name, priceFen, actorUid, key));
        return toPot(pot);
    }

    @Override
    public List<HotpotSlotResp> listSlots(long shopId) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        List<HotpotSlotResp> result = new ArrayList<>();
        List<HotpotSlotDO> rows = slotMapper.selectByShop(shop);
        if (rows == null) {
            return result;
        }
        HotpotFlow.Board board = new HotpotFlow.Board();
        for (HotpotSlotDO row : rows) {
            if (row.getId() == null || row.getShopId() == null) {
                continue;
            }
            HotpotFlow.Slot slot = new HotpotFlow.Slot();
            slot.id = row.getId();
            slot.shopId = row.getShopId();
            slot.capacity = row.getCapacity() == null ? 0 : row.getCapacity();
            slot.occupied = row.getOccupied() == null ? 0 : row.getOccupied();
            board.slots.put(slot.id, slot);
        }
        for (HotpotFlow.Slot slot : HotpotFlow.slotsOf(board, shop)) {
            result.add(toSlot(slot));
        }
        return result;
    }

    @Override
    public List<HotpotStaffHoldResp> listCurrentHolds(long shopId) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        List<HotpotStaffHoldResp> result = new ArrayList<>();
        for (HotpotFlow.StaffHold row : HotpotFlow.currentHolds(load(shop), shop)) {
            HotpotStaffHoldResp item = new HotpotStaffHoldResp();
            item.setId(row.id());
            item.setSlotId(row.slotId());
            item.setShopId(row.shopId());
            item.setUid(row.uid());
            item.setStatus(row.status().name());
            item.setSessionId(row.sessionId());
            result.add(item);
        }
        return result;
    }

    @Override
    public HotpotBoundBillResp boundBill(long shopId, String signCode, long callerUid) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotFlow.BoundBill bill = HotpotFlow.boundBill(load(shop), shop, signCode, callerUid);
        HotpotBoundBillResp resp = new HotpotBoundBillResp();
        resp.setSessionId(bill.sessionId());
        return resp;
    }

    @Override
    public List<HotpotTableResp> listTables(long shopId) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        List<HotpotTableResp> result = new ArrayList<>();
        List<HotpotTableDO> rows = tableMapper.selectByShop(shop);
        if (rows == null) {
            return result;
        }
        for (HotpotTableDO row : rows) {
            if (row.getShopId() == null || row.getShopId() != shop) {
                continue;
            }
            HotpotTableResp item = new HotpotTableResp();
            item.setTableId(row.getId());
            item.setShopId(row.getShopId());
            item.setSignCode(row.getSignCode());
            item.setOccupied(row.getOccupied() != null && row.getOccupied() == 1);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<HotpotPotResp> listPots(long shopId) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        List<HotpotPotResp> result = new ArrayList<>();
        List<ShopPotDO> rows = potMapper.selectByShop(shop);
        if (rows == null) {
            return result;
        }
        for (ShopPotDO row : rows) {
            if (row.getShopId() == null || row.getShopId() != shop || row.getPriceFen() == null) {
                continue;
            }
            result.add(toPotRow(row));
        }
        return result;
    }

    @Override
    public HotpotHoldResp reserve(long shopId, long ownerUid, long slotId, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        assertSlotShop(slotId, shop);
        HotpotFlow.Hold hold = write(shop, requestKey,
                (board, key) -> HotpotFlow.reserve(board, shop, slotId, ownerUid, key));
        return toHold(hold);
    }

    @Override
    public HotpotHoldResp cancel(long actorUid, long holdId) {
        if (actorUid <= 0 || holdId <= 0) {
            throw exception(BAD_REQUEST);
        }
        HotpotHoldDO row = holdMapper.selectByHoldId(holdId);
        if (row == null || row.getShopId() == null) {
            throw exception(NOT_FOUND);
        }
        if (row.getOwnerUid() == null || row.getOwnerUid() != actorUid) {
            throw exception(FORBIDDEN);
        }
        long shop = row.getShopId();
        return transaction().execute(status -> {
            HotpotFlow.Board board = load(shop);
            HotpotFlow.Hold hold = HotpotFlow.cancel(board, actorUid, holdId);
            save(board);
            return toHold(hold);
        });
    }

    @Override
    public HotpotHoldResp seat(long shopId, long holdId, long slotId, long actorUid, boolean staff, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotHoldDO row = holdMapper.selectByHoldId(holdId);
        if (row == null || row.getShopId() == null) {
            throw exception(NOT_FOUND);
        }
        ShopAccess.assertSameShop(row.getShopId(), shop);
        if (!staff && (row.getOwnerUid() == null || row.getOwnerUid() != actorUid)) {
            throw exception(FORBIDDEN);
        }
        HotpotFlow.Hold hold = write(shop, requestKey,
                (board, key) -> HotpotFlow.seat(board, shop, holdId, slotId, actorUid, staff, key));
        return toHold(hold);
    }

    @Override
    public HotpotHoldResp walkIn(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        assertSlotShop(slotId, shop);
        HotpotFlow.Hold hold = write(shop, requestKey,
                (board, key) -> HotpotFlow.walkIn(board, shop, slotId, signCode, actorUid, staff, key));
        return toHold(hold);
    }

    @Override
    public HotpotSessionResp open(long shopId, long slotId, String signCode, long actorUid, boolean staff, String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        assertSlotShop(slotId, shop);
        HotpotFlow.Session session = write(shop, requestKey,
                (board, key) -> HotpotFlow.open(board, shop, slotId, signCode, actorUid, staff, key));
        return toSession(session, null, null);
    }

    @Override
    public HotpotSessionResp addPot(long shopId, long sessionId, long potId, int version, long actorUid, boolean staff,
                                    String requestKey) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        HotpotSessionDO row = sessionMapper.selectBySessionId(sessionId);
        if (row == null || row.getShopId() == null) {
            throw exception(NOT_FOUND);
        }
        ShopAccess.assertSameShop(row.getShopId(), shop);
        if (!staff && (row.getOwnerUid() == null || row.getOwnerUid() != actorUid)) {
            throw exception(FORBIDDEN);
        }
        HotpotFlow.Session session = write(shop, requestKey,
                (board, key) -> HotpotFlow.addPot(board, shop, sessionId, potId, version, actorUid, staff, key));
        return toSession(session, null, null);
    }

    @Override
    public HotpotSessionResp settle(long actorUid, long sessionId, int version, boolean staff, Long staffShop) {
        if (actorUid <= 0 || sessionId <= 0 || version < 0) {
            throw exception(BAD_REQUEST);
        }
        return transaction().execute(status -> {
            HotpotSessionDO row = sessionMapper.selectBySessionId(sessionId);
            if (row == null || row.getShopId() == null) {
                throw exception(NOT_FOUND);
            }
            if (staff) {
                if (staffShop == null) {
                    throw exception(FORBIDDEN);
                }
                ShopAccess.assertSameShop(row.getShopId(), staffShop);
            } else if (row.getOwnerUid() == null || row.getOwnerUid() != actorUid) {
                throw exception(FORBIDDEN);
            }
            HotpotFlow.Board board = load(row.getShopId());
            HotpotFlow.Settlement settlement = HotpotFlow.beginSettlement(board, actorUid, sessionId, version, staff);
            save(board);
            return toSession(settlement.session, settlement.paymentSuccess, settlement.disposition);
        });
    }

    @Override
    public HotpotScanResp scan(long shopId, String signCode) {
        long shop = ShopAccess.requirePositiveShopId(shopId);
        if (signCode == null || signCode.trim().isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        HotpotTableDO row = tableMapper.selectPublic(shop, signCode.trim());
        if (row == null || row.getSignCode() == null) {
            throw exception(NOT_FOUND);
        }
        HotpotScanResp resp = new HotpotScanResp();
        resp.setSignCode(row.getSignCode());
        resp.setOccupied(row.getOccupied() != null && row.getOccupied() == 1);
        return resp;
    }

    private interface Step<T> {
        T apply(HotpotFlow.Board board, String key);
    }

    private <T> T write(long shopId, String rawKey, Step<T> step) {
        return OrderRequestGate.open(rawKey, true,
                key -> {
                    HotpotFlow.Board board = load(shopId);
                    T value = step.apply(board, key);
                    return board.mutated ? null : value;
                },
                key -> transaction().execute(status -> {
                    HotpotFlow.Board board = load(shopId);
                    T value = step.apply(board, key);
                    save(board);
                    return value;
                }));
    }

    private void assertSlotShop(long slotId, long shopId) {
        if (slotId <= 0) {
            throw exception(BAD_REQUEST);
        }
        Long owner = slotMapper.selectShopId(slotId);
        if (owner == null) {
            throw exception(BAD_REQUEST);
        }
        ShopAccess.assertSameShop(owner, shopId);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private HotpotFlow.Board load(long shopId) {
        HotpotFlow.Board board = new HotpotFlow.Board();
        List<HotpotSlotDO> slots = slotMapper.selectByShop(shopId);
        if (slots != null) {
            for (HotpotSlotDO row : slots) {
                if (row.getId() == null || row.getShopId() == null || row.getShopId() != shopId) {
                    continue;
                }
                HotpotFlow.Slot slot = new HotpotFlow.Slot();
                slot.id = row.getId();
                slot.shopId = row.getShopId();
                slot.capacity = row.getCapacity() == null ? 0 : row.getCapacity();
                slot.occupied = row.getOccupied() == null ? 0 : row.getOccupied();
                slot.persisted = true;
                board.slots.put(slot.id, slot);
                board.noteId(slot.id);
            }
        }
        List<HotpotTableDO> tables = tableMapper.selectByShop(shopId);
        if (tables != null) {
            for (HotpotTableDO row : tables) {
                if (row.getId() == null || row.getShopId() == null || row.getShopId() != shopId) {
                    continue;
                }
                HotpotFlow.Table table = new HotpotFlow.Table();
                table.id = row.getId();
                table.shopId = row.getShopId();
                table.signCode = row.getSignCode();
                table.occupied = row.getOccupied() != null && row.getOccupied() == 1;
                table.boundUid = row.getBoundUid();
                table.sessionId = row.getSessionId();
                table.persisted = true;
                board.tables.put(table.id, table);
                board.noteId(table.id);
            }
        }
        List<ShopPotDO> pots = potMapper.selectByShop(shopId);
        if (pots != null) {
            for (ShopPotDO row : pots) {
                if (row.getId() == null || row.getShopId() == null || row.getShopId() != shopId) {
                    continue;
                }
                HotpotFlow.Pot pot = new HotpotFlow.Pot();
                pot.id = row.getId();
                pot.shopId = row.getShopId();
                pot.name = row.getName();
                pot.priceFen = row.getPriceFen();
                pot.persisted = true;
                board.pots.add(pot);
                board.noteId(pot.id);
            }
        }
        List<HotpotHoldDO> holds = holdMapper.selectByShop(shopId);
        if (holds != null) {
            for (HotpotHoldDO row : holds) {
                if (row.getId() == null || row.getShopId() == null || row.getShopId() != shopId) {
                    continue;
                }
                HotpotFlow.Hold hold = new HotpotFlow.Hold();
                hold.id = row.getId();
                hold.shopId = row.getShopId();
                hold.slotId = row.getSlotId() == null ? 0L : row.getSlotId();
                hold.ownerUid = row.getOwnerUid() == null ? 0L : row.getOwnerUid();
                hold.requestKey = row.getRequestKey();
                hold.status = HotpotFlow.HoldStatus.valueOf(row.getStatus());
                hold.persisted = true;
                board.holds.put(hold.id, hold);
                board.noteId(hold.id);
            }
        }
        List<HotpotSessionDO> sessions = sessionMapper.selectByShop(shopId);
        if (sessions != null) {
            for (HotpotSessionDO row : sessions) {
                if (row.getId() == null || row.getShopId() == null || row.getShopId() != shopId) {
                    continue;
                }
                HotpotFlow.Session session = new HotpotFlow.Session();
                session.id = row.getId();
                session.shopId = row.getShopId();
                session.slotId = row.getSlotId() == null ? 0L : row.getSlotId();
                session.tableId = row.getTableId();
                session.holdId = row.getHoldId();
                session.ownerUid = row.getOwnerUid() == null ? 0L : row.getOwnerUid();
                session.version = row.getVersion() == null ? 0 : row.getVersion();
                session.payableFen = row.getPayableFen() == null ? 0 : row.getPayableFen();
                session.paid = false;
                session.zeroPayReleased = row.getZeroPay() != null && row.getZeroPay() == 1;
                session.status = HotpotFlow.SessionStatus.valueOf(row.getStatus());
                session.requestKey = row.getRequestKey();
                session.persisted = true;
                board.sessions.put(session.id, session);
                board.noteId(session.id);
            }
        }
        List<HotpotSessionPotDO> lines = sessionPotMapper.selectByShop(shopId);
        if (lines != null) {
            for (HotpotSessionPotDO row : lines) {
                if (row.getShopId() == null || row.getShopId() != shopId || row.getSessionId() == null) {
                    continue;
                }
                HotpotFlow.Session session = board.sessions.get(row.getSessionId());
                if (session == null) {
                    continue;
                }
                HotpotFlow.SessionPot line = new HotpotFlow.SessionPot();
                line.id = row.getId() == null ? 0L : row.getId();
                line.potId = row.getPotId() == null ? 0L : row.getPotId();
                line.name = row.getName();
                line.priceFen = row.getPriceFen() == null ? 0 : row.getPriceFen();
                line.persisted = true;
                session.pots.add(line);
                board.noteId(line.id);
            }
        }
        List<HotpotRequestDO> requests = requestMapper.selectByShop(shopId);
        if (requests != null) {
            for (HotpotRequestDO row : requests) {
                if (row.getShopId() == null || row.getShopId() != shopId || row.getRequestKey() == null) {
                    continue;
                }
                HotpotFlow.RequestUse use = new HotpotFlow.RequestUse();
                use.id = row.getId() == null ? 0L : row.getId();
                use.shopId = row.getShopId();
                use.requestKey = row.getRequestKey();
                use.ownerUid = row.getOwnerUid() == null ? 0L : row.getOwnerUid();
                use.slotId = row.getSlotId() == null ? 0L : row.getSlotId();
                use.action = row.getAction();
                use.holdId = row.getHoldId();
                use.sessionId = row.getSessionId();
                use.potId = row.getPotId();
                use.resourceId = row.getResourceId();
                use.mark = row.getMark();
                use.persisted = true;
                board.requests.put(row.getShopId() + ":" + row.getRequestKey(), use);
                board.noteId(use.id);
            }
        }
        board.mutated = false;
        return board;
    }

    private void save(HotpotFlow.Board board) {
        Map<Long, Long> slotIds = insertSlots(board);
        pointSlots(board, slotIds);
        Map<Long, Long> tableIds = insertTables(board);
        pointTables(board, tableIds);
        Map<Long, Long> potIds = insertPots(board);
        pointPots(board, potIds);
        applyDeltas(board);
        Map<Long, Long> holdIds = insertHolds(board);
        pointHolds(board, holdIds);
        Map<Long, Long> sessionIds = insertSessions(board);
        pointSessions(board, sessionIds);
        updateDirty(board);
        insertSessionPots(board);
        saveRequests(board);
        board.mutated = false;
    }

    private Map<Long, Long> insertSlots(HotpotFlow.Board board) {
        Map<Long, Long> ids = new HashMap<>();
        for (HotpotFlow.Slot slot : new ArrayList<>(board.slots.values())) {
            if (slot.persisted) {
                continue;
            }
            long previous = slot.id;
            HotpotSlotDO row = new HotpotSlotDO();
            row.setShopId(slot.shopId);
            row.setCapacity(slot.capacity);
            row.setOccupied(slot.occupied);
            slotMapper.insert(row);
            ids.put(previous, row.getId());
            slot.id = row.getId();
            slot.persisted = true;
            slot.occupiedDelta = 0;
            slot.dirty = false;
        }
        return ids;
    }

    private Map<Long, Long> insertTables(HotpotFlow.Board board) {
        Map<Long, Long> ids = new HashMap<>();
        for (HotpotFlow.Table table : new ArrayList<>(board.tables.values())) {
            if (table.persisted) {
                continue;
            }
            long previous = table.id;
            HotpotTableDO row = new HotpotTableDO();
            row.setShopId(table.shopId);
            row.setSignCode(table.signCode);
            row.setOccupied(0);
            tableMapper.insert(row);
            ids.put(previous, row.getId());
            table.id = row.getId();
            table.persisted = true;
            table.dirty = table.occupied || table.boundUid != null || table.sessionId != null;
        }
        return ids;
    }

    private Map<Long, Long> insertPots(HotpotFlow.Board board) {
        Map<Long, Long> ids = new HashMap<>();
        for (HotpotFlow.Pot pot : new ArrayList<>(board.pots)) {
            if (pot.persisted) {
                continue;
            }
            long previous = pot.id;
            ShopPotDO row = new ShopPotDO();
            row.setShopId(pot.shopId);
            row.setName(pot.name);
            row.setPriceFen(pot.priceFen);
            potMapper.insert(row);
            ids.put(previous, row.getId());
            pot.id = row.getId();
            pot.persisted = true;
        }
        return ids;
    }

    private Map<Long, Long> insertHolds(HotpotFlow.Board board) {
        Map<Long, Long> ids = new HashMap<>();
        for (HotpotFlow.Hold hold : new ArrayList<>(board.holds.values())) {
            if (hold.persisted) {
                continue;
            }
            long previous = hold.id;
            HotpotHoldDO row = new HotpotHoldDO();
            row.setShopId(hold.shopId);
            row.setSlotId(hold.slotId);
            row.setOwnerUid(hold.ownerUid);
            row.setRequestKey(hold.requestKey);
            row.setStatus(hold.status.name());
            holdMapper.insert(row);
            ids.put(previous, row.getId());
            hold.id = row.getId();
            hold.persisted = true;
            hold.dirty = false;
        }
        return ids;
    }

    private Map<Long, Long> insertSessions(HotpotFlow.Board board) {
        Map<Long, Long> ids = new HashMap<>();
        for (HotpotFlow.Session session : new ArrayList<>(board.sessions.values())) {
            if (session.persisted) {
                continue;
            }
            long previous = session.id;
            HotpotSessionDO row = new HotpotSessionDO();
            row.setShopId(session.shopId);
            row.setSlotId(session.slotId);
            row.setTableId(session.tableId);
            row.setHoldId(session.holdId);
            row.setOwnerUid(session.ownerUid);
            row.setVersion(session.version);
            row.setPayableFen(session.payableFen);
            row.setZeroPay(session.zeroPayReleased ? 1 : 0);
            row.setStatus(session.status.name());
            row.setRequestKey(session.requestKey);
            sessionMapper.insert(row);
            ids.put(previous, row.getId());
            session.id = row.getId();
            session.persisted = true;
            session.dirty = false;
        }
        return ids;
    }

    private void applyDeltas(HotpotFlow.Board board) {
        for (HotpotFlow.Slot slot : board.slots.values()) {
            if (!slot.persisted || slot.occupiedDelta == 0) {
                continue;
            }
            int updated = slotMapper.addOccupied(slot.id, slot.shopId, slot.occupiedDelta);
            if (updated != 1) {
                throw exception(BAD_REQUEST);
            }
            slot.occupiedDelta = 0;
            slot.dirty = false;
        }
    }

    private void updateDirty(HotpotFlow.Board board) {
        for (HotpotFlow.Hold hold : board.holds.values()) {
            if (hold.persisted && hold.dirty) {
                if (holdMapper.updateStatus(hold.id, hold.shopId, hold.status.name()) != 1) {
                    throw exception(BAD_REQUEST);
                }
                hold.dirty = false;
            }
        }
        for (HotpotFlow.Session session : board.sessions.values()) {
            if (session.persisted && session.dirty) {
                if (sessionMapper.updateSession(session.id, session.shopId, session.tableId, session.holdId,
                        session.version, session.payableFen, session.zeroPayReleased ? 1 : 0,
                        session.status.name()) != 1) {
                    throw exception(BAD_REQUEST);
                }
                session.dirty = false;
            }
        }
        for (HotpotFlow.Table table : board.tables.values()) {
            if (table.persisted && table.dirty) {
                if (tableMapper.updateOccupancy(table.id, table.shopId, table.occupied ? 1 : 0,
                        table.boundUid, table.sessionId) != 1) {
                    throw exception(BAD_REQUEST);
                }
                table.dirty = false;
            }
        }
    }

    private void insertSessionPots(HotpotFlow.Board board) {
        for (HotpotFlow.Session session : board.sessions.values()) {
            for (HotpotFlow.SessionPot line : session.pots) {
                if (line.persisted) {
                    continue;
                }
                HotpotSessionPotDO row = new HotpotSessionPotDO();
                row.setShopId(session.shopId);
                row.setSessionId(session.id);
                row.setPotId(line.potId);
                row.setName(line.name);
                row.setPriceFen(line.priceFen);
                sessionPotMapper.insert(row);
                line.id = row.getId();
                line.persisted = true;
            }
        }
    }

    private void saveRequests(HotpotFlow.Board board) {
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            if (!use.persisted) {
                HotpotRequestDO row = new HotpotRequestDO();
                row.setShopId(use.shopId);
                row.setRequestKey(use.requestKey);
                row.setOwnerUid(use.ownerUid);
                row.setSlotId(use.slotId);
                row.setAction(use.action);
                row.setHoldId(use.holdId);
                row.setSessionId(use.sessionId);
                row.setPotId(use.potId);
                row.setResourceId(use.resourceId);
                row.setMark(use.mark);
                requestMapper.insert(row);
                use.id = row.getId();
                use.persisted = true;
                use.dirty = false;
            } else if (use.dirty) {
                if (requestMapper.updateLinks(use.id, use.shopId, use.holdId, use.sessionId, use.potId) != 1) {
                    throw exception(BAD_REQUEST);
                }
                use.dirty = false;
            }
        }
    }

    private void pointSlots(HotpotFlow.Board board, Map<Long, Long> ids) {
        for (HotpotFlow.Hold hold : board.holds.values()) {
            hold.slotId = mapped(ids, hold.slotId);
        }
        for (HotpotFlow.Session session : board.sessions.values()) {
            session.slotId = mapped(ids, session.slotId);
        }
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            if (use.slotId != 0L) {
                use.slotId = mapped(ids, use.slotId);
            }
            if ("SLOT".equals(use.action)) {
                use.resourceId = mappedObj(ids, use.resourceId);
            }
        }
    }

    private void pointTables(HotpotFlow.Board board, Map<Long, Long> ids) {
        for (HotpotFlow.Session session : board.sessions.values()) {
            session.tableId = mappedObj(ids, session.tableId);
        }
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            if ("TABLE".equals(use.action)) {
                use.resourceId = mappedObj(ids, use.resourceId);
            }
        }
    }

    private void pointPots(HotpotFlow.Board board, Map<Long, Long> ids) {
        for (HotpotFlow.Session session : board.sessions.values()) {
            for (HotpotFlow.SessionPot line : session.pots) {
                line.potId = mapped(ids, line.potId);
            }
        }
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            use.potId = mappedObj(ids, use.potId);
            if ("POT".equals(use.action)) {
                use.resourceId = mappedObj(ids, use.resourceId);
            }
        }
    }

    private void pointHolds(HotpotFlow.Board board, Map<Long, Long> ids) {
        for (HotpotFlow.Session session : board.sessions.values()) {
            session.holdId = mappedObj(ids, session.holdId);
        }
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            use.holdId = mappedObj(ids, use.holdId);
        }
    }

    private void pointSessions(HotpotFlow.Board board, Map<Long, Long> ids) {
        for (HotpotFlow.Table table : board.tables.values()) {
            table.sessionId = mappedObj(ids, table.sessionId);
        }
        for (HotpotFlow.RequestUse use : board.requests.values()) {
            use.sessionId = mappedObj(ids, use.sessionId);
        }
    }

    private static long mapped(Map<Long, Long> ids, long id) {
        Long mapped = ids.get(id);
        return mapped == null ? id : mapped;
    }

    private static Long mappedObj(Map<Long, Long> ids, Long id) {
        if (id == null) {
            return null;
        }
        Long mapped = ids.get(id);
        return mapped == null ? id : mapped;
    }

    private static HotpotHoldResp toHold(HotpotFlow.Hold hold) {
        HotpotHoldResp resp = new HotpotHoldResp();
        resp.setHoldId(hold.id);
        resp.setShopId(hold.shopId);
        resp.setSlotId(hold.slotId);
        resp.setStatus(hold.status.name());
        resp.setRequestKey(hold.requestKey);
        resp.setLateAutoCancels(HotpotFlow.lateAutoCancels());
        return resp;
    }

    private static HotpotSessionResp toSession(HotpotFlow.Session session, Boolean paymentSuccess, String disposition) {
        HotpotSessionResp resp = new HotpotSessionResp();
        resp.setSessionId(session.id);
        resp.setShopId(session.shopId);
        resp.setSlotId(session.slotId);
        resp.setVersion(session.version);
        resp.setPayableFen(session.payableFen);
        resp.setStatus(session.status.name());
        resp.setPaid(false);
        resp.setZeroPayReleased(session.zeroPayReleased);
        resp.setPaymentSuccess(paymentSuccess);
        resp.setDisposition(disposition);
        List<HotpotSessionResp.PotLine> lines = new ArrayList<>();
        for (HotpotFlow.SessionPot pot : session.pots) {
            HotpotSessionResp.PotLine line = new HotpotSessionResp.PotLine();
            line.setPotId(pot.potId);
            line.setName(pot.name);
            line.setPriceFen(pot.priceFen);
            lines.add(line);
        }
        resp.setPots(lines);
        return resp;
    }

    private static HotpotSlotResp toSlot(HotpotFlow.Slot slot) {
        HotpotSlotResp resp = new HotpotSlotResp();
        resp.setSlotId(slot.id);
        resp.setShopId(slot.shopId);
        resp.setCapacity(slot.capacity);
        resp.setOccupied(slot.occupied);
        return resp;
    }

    private static HotpotTableResp toTable(HotpotFlow.Table table) {
        HotpotTableResp resp = new HotpotTableResp();
        resp.setTableId(table.id);
        resp.setShopId(table.shopId);
        resp.setSignCode(table.signCode);
        resp.setOccupied(table.occupied);
        return resp;
    }

    private static HotpotPotResp toPot(HotpotFlow.Pot pot) {
        HotpotPotResp resp = new HotpotPotResp();
        resp.setPotId(pot.id);
        resp.setShopId(pot.shopId);
        resp.setName(pot.name);
        resp.setPriceFen(pot.priceFen);
        return resp;
    }

    private static HotpotPotResp toPotRow(ShopPotDO row) {
        HotpotPotResp resp = new HotpotPotResp();
        resp.setPotId(row.getId());
        resp.setShopId(row.getShopId());
        resp.setName(row.getName());
        resp.setPriceFen(row.getPriceFen());
        return resp;
    }

}
