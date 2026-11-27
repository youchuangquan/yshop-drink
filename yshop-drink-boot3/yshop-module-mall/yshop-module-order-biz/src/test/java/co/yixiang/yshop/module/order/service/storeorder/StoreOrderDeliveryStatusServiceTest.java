package co.yixiang.yshop.module.order.service.storeorder;

import co.yixiang.yshop.framework.common.constant.ShopConstants;
import co.yixiang.yshop.framework.common.enums.OrderInfoEnum;
import co.yixiang.yshop.framework.common.exception.ServiceException;
import co.yixiang.yshop.framework.common.order.PayChannels;
import co.yixiang.yshop.framework.common.order.PaymentReconciliation;
import co.yixiang.yshop.framework.security.core.LoginUser;
import co.yixiang.yshop.module.member.service.user.MemberUserService;
import co.yixiang.yshop.module.order.controller.admin.storeorder.vo.StoreOrderDeliveryStatusReqVO;
import co.yixiang.yshop.module.order.controller.admin.storeorder.vo.StoreOrderRespVO;
import co.yixiang.yshop.module.order.controller.admin.storeorder.vo.StoreOrderUpdateReqVO;
import co.yixiang.yshop.module.order.controller.app.order.vo.AppStoreOrderQueryVo;
import co.yixiang.yshop.module.order.dal.dataobject.storeorder.StoreOrderDO;
import co.yixiang.yshop.module.order.dal.dataobject.storeorderstatus.StoreOrderStatusDO;
import co.yixiang.yshop.module.order.dal.mysql.storeorder.StoreOrderMapper;
import co.yixiang.yshop.module.order.dal.mysql.storeorderstatus.StoreOrderStatusMapper;
import co.yixiang.yshop.module.order.enums.OrderLogEnum;
import co.yixiang.yshop.module.order.enums.UpdateOrderEnum;
import co.yixiang.yshop.module.order.service.storeorderstatus.StoreOrderStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static co.yixiang.yshop.module.order.enums.ErrorCodeConstants.ORDER_PAY_CHANNEL_CLOSED;
import static co.yixiang.yshop.module.order.enums.ErrorCodeConstants.ORDER_STATUS_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreOrderDeliveryStatusServiceTest {

    private static final BigDecimal PAY_PRICE = new BigDecimal("12.50");
    private static final BigDecimal PACKING = new BigDecimal("1.00");
    private static final BigDecimal POSTAGE = new BigDecimal("3.00");
    private static final String ADDRESS = "道路 1 号";

    private StoreOrderServiceImpl service;
    private StoreOrderMapper storeOrderMapper;
    private StoreOrderStatusMapper storeOrderStatusMapper;
    private StoreOrderStatusService storeOrderStatusService;
    private AppStoreOrderService appStoreOrderService;
    private RedissonClient redissonClient;
    private List<StoreOrderStatusDO> records;
    private StoreOrderDO order;

    @BeforeEach
    void setUp() throws Exception {
        service = new StoreOrderServiceImpl();
        storeOrderMapper = mock(StoreOrderMapper.class);
        storeOrderStatusMapper = mock(StoreOrderStatusMapper.class);
        storeOrderStatusService = mock(StoreOrderStatusService.class);
        appStoreOrderService = mock(AppStoreOrderService.class);
        redissonClient = mock(RedissonClient.class);
        records = new ArrayList<>();
        order = takeout();
        inject("storeOrderMapper", storeOrderMapper);
        inject("storeOrderStatusMapper", storeOrderStatusMapper);
        inject("storeOrderStatusService", storeOrderStatusService);
        inject("appStoreOrderService", appStoreOrderService);
        inject("userService", mock(MemberUserService.class));
        inject("redissonClient", redissonClient);

        when(storeOrderMapper.selectById(10L)).thenAnswer(inv -> order);
        when(storeOrderStatusMapper.selectByOrderAndType(10L, TakeoutDeliveryStatus.CHANGE_TYPE))
                .thenAnswer(inv -> new ArrayList<>(records));
        doAnswer(inv -> {
            StoreOrderStatusDO row = new StoreOrderStatusDO();
            row.setOid(inv.getArgument(1));
            row.setChangeType(inv.getArgument(2));
            row.setChangeMessage(inv.getArgument(3));
            records.add(row);
            return null;
        }).when(storeOrderStatusService).create(any(), any(), any(), any());
        when(storeOrderMapper.updateTakeoutDeliveryStatus(any(), any(), any(), any())).thenAnswer(inv -> {
            Long shopId = inv.getArgument(1);
            Integer from = inv.getArgument(2);
            Integer to = inv.getArgument(3);
            if (shopId == null || !shopId.equals(order.getShopId())) {
                return 0;
            }
            if (!OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid())
                    || !OrderLogEnum.ORDER_TAKE_OUT.getValue().equals(order.getOrderType())
                    || !OrderInfoEnum.REFUND_STATUS_0.getValue().equals(order.getRefundStatus())
                    || !from.equals(order.getStatus())) {
                return 0;
            }
            order.setStatus(to);
            return 1;
        });
        login(1L);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void movesForwardAndSameKeyReplayDoesNotWriteAgain() {
        StoreOrderRespVO moved = service.updateDeliveryStatus(req(1, null, "delivery-key-01"));
        assertDeliveryBody(moved, 1, "待收货");
        StoreOrderRespVO replay = service.updateDeliveryStatus(req(1, null, "delivery-key-01"));
        assertDeliveryBody(replay, 1, "待收货");
        assertEquals(1, order.getStatus());
        assertEquals(1, deliveryRecords().size());
        assertTrue(deliveryRecords().get(0).getChangeMessage().startsWith("待收货"));
        assertTrue(deliveryRecords().get(0).getChangeMessage().contains("delivery-key-01"));
        assertMoneyAndAddress();

        ServiceException reused = assertThrows(ServiceException.class,
                () -> service.updateDeliveryStatus(req(2, null, "delivery-key-01")));
        assertEquals(ORDER_STATUS_ERROR.getCode(), reused.getCode());
        assertEquals(1, order.getStatus());
        assertEquals(1, deliveryRecords().size());

        service.updateDeliveryStatus(req(2, 1L, "delivery-key-02"));
        service.updateDeliveryStatus(req(2, 1L, "delivery-key-02"));
        assertEquals(2, order.getStatus());
        assertEquals(2, deliveryRecords().size());
        assertTrue(deliveryRecords().get(1).getChangeMessage().startsWith("已收货"));
        assertFalse(deliveryRecords().get(1).getChangeMessage().contains("已送达"));
        assertEquals(OrderInfoEnum.PAY_STATUS_1.getValue(), order.getPaid());
        assertMoneyAndAddress();
        verify(storeOrderMapper, never()).updateById(any());
    }

    @Test
    void lostRaceWithTheSameKeyDoesNotInsertASecondRecord() {
        when(storeOrderMapper.updateTakeoutDeliveryStatus(any(), any(), any(), any())).thenAnswer(inv -> {
            order.setStatus(1);
            StoreOrderStatusDO row = new StoreOrderStatusDO();
            row.setOid(10L);
            row.setChangeType(TakeoutDeliveryStatus.CHANGE_TYPE);
            row.setChangeMessage(TakeoutDeliveryStatus.operationMessage(1, "delivery-key-01"));
            records.add(row);
            return 0;
        });

        service.updateDeliveryStatus(req(1, null, "delivery-key-01"));

        assertEquals(1, order.getStatus());
        assertEquals(1, records.size());
        verify(storeOrderStatusService, never()).create(any(), any(), any(), any());
    }

    @Test
    void missingKeyReplaysSameTargetWithoutAnotherRecord() {
        service.updateDeliveryStatus(req(1, null, null));
        service.updateDeliveryStatus(req(1, null, null));
        service.updateDeliveryStatus(req(2, null, null));
        StoreOrderRespVO again = service.updateDeliveryStatus(req(2, null, null));
        assertDeliveryBody(again, 2, "待评价");
        assertEquals(2, order.getStatus());
        assertEquals(2, deliveryRecords().size());
        assertNull(TakeoutDeliveryStatus.keyOf(deliveryRecords().get(0).getChangeMessage()));
    }

    @Test
    void unpaidNonTakeoutSkipRollbackAndRefundDoNotWrite() {
        order.setPaid(OrderInfoEnum.PAY_STATUS_0.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-01")));
        assertEquals(0, order.getPaid());
        assertEquals(0, order.getStatus());

        order.setPaid(OrderInfoEnum.PAY_STATUS_1.getValue());
        order.setOrderType(OrderLogEnum.ORDER_TAKE_IN.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-01")));
        order.setOrderType(OrderLogEnum.ORDER_TAKE_DESK.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-01")));

        order.setOrderType(OrderLogEnum.ORDER_TAKE_OUT.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(2, null, "delivery-key-01")));
        order.setStatus(2);
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-02")));
        assertEquals(2, order.getStatus());
        order.setStatus(1);
        assertClosed(() -> service.updateDeliveryStatus(req(0, null, "delivery-key-03")));
        assertClosed(() -> service.updateDeliveryStatus(req(3, null, "delivery-key-04")));

        order.setStatus(0);
        order.setRefundStatus(OrderInfoEnum.REFUND_STATUS_1.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-05")));
        order.setRefundStatus(OrderInfoEnum.REFUND_STATUS_2.getValue());
        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-06")));
        assertEquals(0, order.getStatus());
        assertEquals(0, deliveryRecords().size());
        verify(storeOrderMapper, never()).updateTakeoutDeliveryStatus(any(), any(), any(), any());
        assertMoneyAndAddress();
    }

    @Test
    void otherShopIsForbiddenEvenWhenTheBodyNamesThatShop() {
        order.setShopId(2L);
        ServiceException named = assertThrows(ServiceException.class,
                () -> service.updateDeliveryStatus(req(1, 2L, "delivery-key-01")));
        assertEquals(FORBIDDEN.getCode(), named.getCode());
        verify(storeOrderMapper, never()).selectById(any());
        verify(storeOrderMapper, never()).updateTakeoutDeliveryStatus(any(), any(), any(), any());

        ServiceException omitted = assertThrows(ServiceException.class,
                () -> service.updateDeliveryStatus(req(1, null, "delivery-key-01")));
        assertEquals(FORBIDDEN.getCode(), omitted.getCode());

        ServiceException echoed = assertThrows(ServiceException.class,
                () -> service.updateDeliveryStatus(req(1, 1L, "delivery-key-01")));
        assertEquals(FORBIDDEN.getCode(), echoed.getCode());
        assertEquals(0, order.getStatus());
        assertEquals(0, records.size());
        verify(storeOrderMapper, never()).updateTakeoutDeliveryStatus(any(), any(), any(), any());
    }

    @Test
    void yueAndCashPaymentKeepStatusThenDeliveryCanMove() {
        order.setPaid(OrderInfoEnum.PAY_STATUS_0.getValue());
        order.setPayType("yue");
        MemNotices notices = new MemNotices();
        PaymentReconciliation.Result paid = pay(order, "yue", PayChannels.localNotifyKey("yue", order.getOrderId()), notices);
        PaymentReconciliation.Result replay = pay(order, "yue", PayChannels.localNotifyKey("yue", order.getOrderId()), notices);
        assertEquals(PaymentReconciliation.Disposition.APPLIED, paid.disposition());
        assertEquals(PaymentReconciliation.Disposition.REPLAY, replay.disposition());
        assertFalse(replay.settled());
        assertEquals(OrderInfoEnum.PAY_STATUS_1.getValue(), order.getPaid());
        assertEquals(OrderInfoEnum.STATUS_0.getValue(), order.getStatus());
        assertEquals("yue", order.getPayType());

        service.updateDeliveryStatus(req(1, null, "yue-step-0001"));
        service.updateDeliveryStatus(req(1, null, "yue-step-0001"));
        assertEquals(1, order.getStatus());
        assertEquals(1, deliveryRecords().size());

        order.setStatus(0);
        order.setPaid(OrderInfoEnum.PAY_STATUS_0.getValue());
        order.setPayType("cash");
        records.clear();
        PaymentReconciliation.Result cash = pay(order, "cash", PayChannels.localNotifyKey("cash", order.getOrderId()), new MemNotices());
        assertEquals(PaymentReconciliation.Disposition.APPLIED, cash.disposition());
        assertTrue(cash.settled());
        assertEquals(0, order.getStatus());
        assertEquals("cash", order.getPayType());
        service.updateDeliveryStatus(req(1, null, "cash-step-0001"));
        service.updateDeliveryStatus(req(2, null, "cash-step-0002"));
        assertEquals(2, order.getStatus());
        assertEquals(OrderInfoEnum.PAY_STATUS_1.getValue(), order.getPaid());
        assertEquals(2, deliveryRecords().size());
        assertMoneyAndAddress();
    }

    @Test
    void weixinAndAlipayStayUnpaidSoDeliveryStaysClosed() {
        assertFalse(PayChannels.settles("weixin"));
        assertFalse(PayChannels.settles("alipay"));
        assertEquals(1008007024, ORDER_PAY_CHANNEL_CLOSED.getCode());
        order.setPaid(OrderInfoEnum.PAY_STATUS_0.getValue());
        MemNotices notices = new MemNotices();
        PaymentReconciliation.Result weixin = pay(order, "weixin", "4200000000000000000000000001", notices);
        PaymentReconciliation.Result alipay = pay(order, "alipay", "2024112622001400000000000001", notices);
        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, weixin.disposition());
        assertEquals(PaymentReconciliation.Disposition.UNCONNECTED, alipay.disposition());
        assertFalse(weixin.settled());
        assertEquals(OrderInfoEnum.PAY_STATUS_0.getValue(), order.getPaid());
        assertEquals(OrderInfoEnum.STATUS_0.getValue(), order.getStatus());

        assertClosed(() -> service.updateDeliveryStatus(req(1, null, "delivery-key-01")));
        assertEquals(0, order.getPaid());
        assertEquals(0, order.getStatus());
        assertEquals(0, records.size());
        assertMoneyAndAddress();
    }

    @Test
    void orderSendAndTakeDoNotWriteTakeoutStatusOrEnqueueAutoReceive() {
        StoreOrderUpdateReqVO send = new StoreOrderUpdateReqVO();
        send.setId(10L);
        send.setUpdateType(UpdateOrderEnum.ORDER_SEND.getValue());
        send.setOrderType(OrderLogEnum.ORDER_TAKE_IN.getValue());
        send.setStatus(OrderInfoEnum.STATUS_1.getValue());
        send.setOrderId(order.getOrderId());
        service.updateStoreOrder(send);
        assertEquals(0, order.getStatus());
        verify(redissonClient, never()).getBlockingDeque(anyString());
        verify(appStoreOrderService, never()).takeOrder(any(), any(), any());
        verify(storeOrderMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                patch -> Integer.valueOf(0).equals(patch.getStatus())));

        order.setOrderType(OrderLogEnum.ORDER_TAKE_IN.getValue());
        service.takeStoreOrder(10L);
        verify(appStoreOrderService).takeOrder(order.getOrderId(), order.getUid(), order.getShopId());

        order.setOrderType(OrderLogEnum.ORDER_TAKE_OUT.getValue());
        ServiceException denied = assertThrows(ServiceException.class, () -> service.takeStoreOrder(10L));
        assertEquals(ORDER_STATUS_ERROR.getCode(), denied.getCode());
        verify(appStoreOrderService).takeOrder(order.getOrderId(), order.getUid(), order.getShopId());
        assertEquals(0, order.getStatus());
    }

    @Test
    void orderSendKeepsTheStoredTakeoutStatus() {
        order.setStatus(2);
        StoreOrderUpdateReqVO send = new StoreOrderUpdateReqVO();
        send.setId(10L);
        send.setUpdateType(UpdateOrderEnum.ORDER_SEND.getValue());
        send.setOrderId(order.getOrderId());
        service.updateStoreOrder(send);

        org.mockito.ArgumentCaptor<StoreOrderDO> captor = org.mockito.ArgumentCaptor.forClass(StoreOrderDO.class);
        verify(storeOrderMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(2), captor.getValue().getStatus());
        order.setStatus(captor.getValue().getStatus());
        assertEquals(Integer.valueOf(2), order.getStatus());
        assertEquals(OrderInfoEnum.PAY_STATUS_1.getValue(), order.getPaid());
        assertMoneyAndAddress();
    }

    @Test
    void deskSendStillEnqueuesAndTakeoutTakeOrderWritesNothing() throws Exception {
        @SuppressWarnings("unchecked")
        RBlockingDeque<Object> deque = mock(RBlockingDeque.class);
        @SuppressWarnings("unchecked")
        RDelayedQueue<Object> delayed = mock(RDelayedQueue.class);
        when(redissonClient.getBlockingDeque(ShopConstants.REDIS_ORDER_OUTTIME_UNCONFIRM)).thenReturn(deque);
        when(redissonClient.getDelayedQueue(deque)).thenReturn(delayed);
        order.setOrderType(OrderLogEnum.ORDER_TAKE_DESK.getValue());
        StoreOrderUpdateReqVO desk = new StoreOrderUpdateReqVO();
        desk.setId(10L);
        desk.setOrderId(order.getOrderId());
        desk.setUpdateType(UpdateOrderEnum.ORDER_SEND.getValue());
        service.updateStoreOrder(desk);
        verify(delayed).offer(any(), eq((long) ShopConstants.ORDER_OUTTIME_UNCONFIRM), eq(TimeUnit.MINUTES));

        TakeProbe probe = new TakeProbe();
        AppStoreOrderQueryVo takeout = new AppStoreOrderQueryVo();
        takeout.setId(10L);
        takeout.setOrderId("T10001");
        takeout.setUid(42L);
        takeout.setShopId(1L);
        takeout.setPaid(OrderInfoEnum.PAY_STATUS_1.getValue());
        takeout.setStatus(OrderInfoEnum.STATUS_1.getValue());
        takeout.setOrderType(OrderLogEnum.ORDER_TAKE_OUT.getValue());
        probe.order = takeout;
        ServiceException denied = assertThrows(ServiceException.class, () -> probe.takeOrder("T10001", 42L, 1L));
        assertEquals(ORDER_STATUS_ERROR.getCode(), denied.getCode());
        assertEquals(0, probe.updates);
        assertEquals(OrderInfoEnum.STATUS_1.getValue(), takeout.getStatus());
    }

    private void assertClosed(Runnable call) {
        ServiceException ex = assertThrows(ServiceException.class, call::run);
        assertEquals(ORDER_STATUS_ERROR.getCode(), ex.getCode());
    }

    private List<StoreOrderStatusDO> deliveryRecords() {
        List<StoreOrderStatusDO> rows = new ArrayList<>();
        for (StoreOrderStatusDO row : records) {
            if (TakeoutDeliveryStatus.CHANGE_TYPE.equals(row.getChangeType())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private void assertMoneyAndAddress() {
        assertEquals(0, PAY_PRICE.compareTo(order.getPayPrice()));
        assertEquals(0, PACKING.compareTo(order.getPackingPrice()));
        assertEquals(0, POSTAGE.compareTo(order.getTotalPostage()));
        assertEquals(0, POSTAGE.compareTo(order.getPayPostage()));
        assertEquals(ADDRESS, order.getUserAddress());
    }

    private void assertDeliveryBody(StoreOrderRespVO body, int status, String statusStr) {
        assertEquals(order.getId(), body.getId());
        assertEquals(order.getOrderId(), body.getOrderId());
        assertEquals(order.getOrderType(), body.getOrderType());
        assertEquals(order.getPaid(), body.getPaid());
        assertEquals(order.getRefundStatus(), body.getRefundStatus());
        assertEquals(Integer.valueOf(status), body.getStatus());
        assertEquals(statusStr, body.getStatusStr());
        assertEquals(ADDRESS, body.getUserAddress());
        assertEquals(0, PACKING.compareTo(body.getPackingPrice()));
        assertEquals(0, POSTAGE.compareTo(body.getPayPostage()));
        assertEquals(0, PAY_PRICE.compareTo(body.getPayPrice()));
        assertEquals(order.getShopId(), body.getShopId());
    }

    private static StoreOrderDO takeout() {
        StoreOrderDO row = new StoreOrderDO();
        row.setId(10L);
        row.setOrderId("T10001");
        row.setUid(42L);
        row.setShopId(1L);
        row.setPaid(OrderInfoEnum.PAY_STATUS_1.getValue());
        row.setStatus(OrderInfoEnum.STATUS_0.getValue());
        row.setRefundStatus(OrderInfoEnum.REFUND_STATUS_0.getValue());
        row.setOrderType(OrderLogEnum.ORDER_TAKE_OUT.getValue());
        row.setPayType("yue");
        row.setPayPrice(PAY_PRICE);
        row.setPackingPrice(PACKING);
        row.setTotalPostage(POSTAGE);
        row.setPayPostage(POSTAGE);
        row.setUserAddress(ADDRESS);
        return row;
    }

    private static StoreOrderDeliveryStatusReqVO req(int status, Long shopId, String requestKey) {
        StoreOrderDeliveryStatusReqVO req = new StoreOrderDeliveryStatusReqVO();
        req.setId(10L);
        req.setStatus(status);
        req.setShopId(shopId);
        req.setRequestKey(requestKey);
        return req;
    }

    private static void login(long shopId) {
        LoginUser user = new LoginUser();
        user.setId(7L);
        user.setShopId(shopId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void inject(String name, Object value) throws Exception {
        Field field = StoreOrderServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static PaymentReconciliation.Result pay(StoreOrderDO order, String payType, String notifyKey,
                                                    MemNotices notices) {
        AtomicInteger completes = new AtomicInteger();
        int statusBefore = order.getStatus();
        PaymentReconciliation.Result result = PaymentReconciliation.apply(notifyKey, payType, order.getOrderId(),
                new PaymentReconciliation.Orders() {
                    @Override
                    public PaymentReconciliation.OrderState load(String id) {
                        boolean paid = OrderInfoEnum.PAY_STATUS_1.getValue().equals(order.getPaid());
                        boolean completed = OrderInfoEnum.STATUS_3.getValue().equals(order.getStatus());
                        boolean refunding = order.getRefundStatus() != null
                                && !OrderInfoEnum.REFUND_STATUS_0.getValue().equals(order.getRefundStatus());
                        boolean awaiting = OrderInfoEnum.STATUS_0.getValue().equals(order.getStatus());
                        return new PaymentReconciliation.OrderState(!paid && !completed && !refunding && awaiting, paid, completed);
                    }

                    @Override
                    public boolean markPaidIfUnpaid(String id, String type) {
                        if (!OrderInfoEnum.PAY_STATUS_0.getValue().equals(order.getPaid())) {
                            return false;
                        }
                        order.setPaid(OrderInfoEnum.PAY_STATUS_1.getValue());
                        order.setPayType(type);
                        return true;
                    }
                }, notices, (id, key, type) -> {
                    throw new AssertionError("late hold");
                }, completes::incrementAndGet);
        assertEquals(statusBefore, order.getStatus());
        if (PayChannels.settles(payType) && result.disposition() == PaymentReconciliation.Disposition.APPLIED) {
            assertEquals(1, completes.get());
        } else {
            assertEquals(0, completes.get());
        }
        return result;
    }

    private static final class MemNotices implements PaymentReconciliation.Notices {
        private final Map<String, PaymentReconciliation.Disposition> rows = new HashMap<>();

        @Override
        public PaymentReconciliation.Disposition find(String notifyKey) {
            return rows.get(notifyKey);
        }

        @Override
        public boolean claim(String notifyKey, PaymentReconciliation.Disposition disposition) {
            return rows.putIfAbsent(notifyKey, disposition) == null;
        }

        @Override
        public void replace(String notifyKey, PaymentReconciliation.Disposition disposition) {
            rows.put(notifyKey, disposition);
        }
    }

    private static final class TakeProbe extends AppStoreOrderServiceImpl {
        private AppStoreOrderQueryVo order;
        private int updates;

        @Override
        public AppStoreOrderQueryVo getOrderInfo(String unique, Long uid) {
            return order;
        }

        @Override
        public boolean updateById(StoreOrderDO entity) {
            updates++;
            if (entity.getStatus() != null && order != null) {
                order.setStatus(entity.getStatus());
            }
            return true;
        }
    }

}
