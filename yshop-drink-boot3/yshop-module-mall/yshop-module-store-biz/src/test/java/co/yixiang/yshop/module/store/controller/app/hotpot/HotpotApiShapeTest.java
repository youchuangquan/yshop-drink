package co.yixiang.yshop.module.store.controller.app.hotpot;

import co.yixiang.yshop.module.store.controller.admin.hotpot.AdminHotpotController;
import co.yixiang.yshop.module.store.controller.admin.hotpot.vo.AdminHotpotCommands;
import co.yixiang.yshop.framework.security.core.annotations.PreAuthenticated;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.AppHotpotCommands;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotBoundBillResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotStaffHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;
import co.yixiang.yshop.module.store.dal.dataobject.hotpot.HotpotSessionDO;
import co.yixiang.yshop.module.store.service.hotpot.HotpotService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotApiShapeTest {

    @Test
    void cancelAndSettleBodiesStayNarrow() {
        assertEquals(List.of("holdId"), names(AppHotpotCommands.Cancel.class));
        assertEquals(List.of("sessionId", "version"), names(AppHotpotCommands.Settle.class));
        assertEquals(List.of("sessionId", "version"), names(AdminHotpotCommands.Settle.class));
        assertEquals(List.of("signCode", "occupied"), names(HotpotScanResp.class));
    }

    @Test
    void responsesDoNotCarryCustomerIdentityOrAPaidAmount() {
        for (Class<?> type : new Class<?>[]{
                HotpotScanResp.class, HotpotHoldResp.class, HotpotTableResp.class, AppHotpotCommands.Settle.class,
                AppHotpotCommands.Cancel.class, AdminHotpotCommands.Settle.class}) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertFalse(name.contains("phone"), type.getSimpleName());
                assertFalse(name.contains("owner"), type.getSimpleName());
                assertFalse(name.contains("bound"), type.getSimpleName());
                assertFalse(name.contains("amount"), type.getSimpleName());
                assertFalse(name.contains("paidfen"), type.getSimpleName());
            }
        }
    }

    @Test
    void thereIsNoReplaceSlotsCampaignOrCustomerReservationListOrPayDeclaration() {
        for (Method method : HotpotService.class.getMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("replaceslot"));
            assertFalse(name.contains("myreservation"));
            assertFalse(name.contains("remind"));
        }
        for (Class<?> type : new Class<?>[]{AppHotpotController.class, AdminHotpotController.class}) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                assertFalse(name.contains("replace"));
                assertFalse(name.equals("pay"));
                assertFalse(name.contains("amount"));
            }
        }
        assertFalse(names(HotpotSessionResp.class).contains("paidFen"));
    }

    @Test
    void queryApisStayOnTheConfirmedShapes() throws Exception {
        assertEquals(List.of("slotId", "shopId", "capacity", "occupied", "startTime", "endTime"),
                names(HotpotSlotResp.class));
        assertEquals(List.of("id", "slotId", "shopId", "uid", "status", "sessionId"), names(HotpotStaffHoldResp.class));
        assertEquals(List.of("sessionId", "version", "payableFen", "status", "paid", "zeroPayReleased",
                "feesWaived", "pots", "dishes"),
                names(HotpotBoundBillResp.class));
        assertFalse(names(HotpotSessionDO.class).stream().anyMatch(name -> name.toLowerCase().contains("companion")));
        assertEquals(List.of("holdId", "slotId", "signCode", "requestKey"),
                names(AdminHotpotCommands.ReservationSeat.class));
        assertEquals(List.of("shopId", "slotId", "signCode", "productId", "quantity", "version", "requestKey"),
                names(AppHotpotCommands.OrderDish.class));
        for (String forbidden : new String[]{"phone", "owner", "paidfen", "amount", "duration", "retain"}) {
            for (Field field : HotpotBoundBillResp.class.getDeclaredFields()) {
                assertFalse(field.getName().toLowerCase().contains(forbidden), field.getName());
            }
        }
        for (String forbidden : new String[]{"requestkey", "name", "phone", "party", "arrival", "tabletype"}) {
            for (Field field : HotpotStaffHoldResp.class.getDeclaredFields()) {
                assertFalse(field.getName().toLowerCase().contains(forbidden));
            }
        }

        Method slots = AppHotpotController.class.getMethod("listSlots", Long.class);
        assertNotNull(slots.getAnnotation(PreAuthenticated.class));
        assertEquals("/slot", slots.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("shopId", slots.getParameters()[0].getAnnotation(RequestParam.class).value());

        Method bill = AppHotpotController.class.getMethod("boundBill", Long.class, String.class);
        assertNotNull(bill.getAnnotation(PreAuthenticated.class));
        assertEquals("/bill", bill.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("shopId", bill.getParameters()[0].getAnnotation(RequestParam.class).value());
        assertEquals("signCode", bill.getParameters()[1].getAnnotation(RequestParam.class).value());

        Method scan = AppHotpotController.class.getMethod("scan", Long.class, String.class);
        assertNull(scan.getAnnotation(PreAuthenticated.class));

        Method dish = AppHotpotController.class.getMethod("orderDish", AppHotpotCommands.OrderDish.class);
        assertNotNull(dish.getAnnotation(PreAuthenticated.class));
        assertEquals("/session/dish", dish.getAnnotation(PostMapping.class).value()[0]);

        Method seatReservation = AdminHotpotController.class.getMethod("seatReservation",
                AdminHotpotCommands.ReservationSeat.class);
        assertNotNull(seatReservation.getAnnotation(PreAuthorize.class));
        assertEquals("/reservation/seat", seatReservation.getAnnotation(PostMapping.class).value()[0]);
        assertEquals(1, seatReservation.getParameterCount());

        Method holds = AdminHotpotController.class.getMethod("listHolds");
        assertNotNull(holds.getAnnotation(PreAuthorize.class));
        assertEquals("/hold", holds.getAnnotation(GetMapping.class).value()[0]);
        assertEquals(0, holds.getParameterCount());
    }

    @Test
    void staffServeAndRefundStayOffTheCustomerApi() throws Exception {
        assertEquals(List.of("sessionId", "version", "requestKey", "items"), names(AdminHotpotCommands.Serve.class));
        assertEquals(List.of("sessionId", "version", "requestKey", "items"), names(AdminHotpotCommands.Refund.class));
        assertEquals(List.of("lineId", "quantity"), names(AdminHotpotCommands.LineQty.class));
        assertEquals(List.of("lineId", "productId", "name", "priceFen", "quantity", "servedQty", "refundedQty",
                "refundableQty"), names(HotpotSessionResp.DishLine.class));
        assertEquals(List.of("lineId", "productId", "name", "priceFen", "quantity", "servedQty", "refundedQty",
                "refundableQty"), names(HotpotBoundBillResp.DishLine.class));
        assertTrue(names(HotpotSessionResp.class).contains("signCode"));
        assertTrue(names(HotpotSessionResp.class).contains("feesWaived"));
        assertFalse(names(AdminHotpotCommands.Serve.class).contains("shopId"));
        assertFalse(names(AdminHotpotCommands.Refund.class).contains("shopId"));

        Method list = AdminHotpotController.class.getMethod("listSessions");
        assertEquals("/session", list.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('store:shop:query')", list.getAnnotation(PreAuthorize.class).value());
        assertEquals(0, list.getParameterCount());

        Method serve = AdminHotpotController.class.getMethod("serve", AdminHotpotCommands.Serve.class);
        assertEquals("/session/serve", serve.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('store:shop:query')", serve.getAnnotation(PreAuthorize.class).value());
        assertEquals(1, serve.getParameterCount());

        Method refund = AdminHotpotController.class.getMethod("refund", AdminHotpotCommands.Refund.class);
        assertEquals("/session/refund", refund.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('store:shop:update')", refund.getAnnotation(PreAuthorize.class).value());

        for (Method method : AppHotpotController.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("refund"), method.getName());
            assertFalse(name.equals("serve"), method.getName());
        }
        for (Class<?> nested : AppHotpotCommands.class.getDeclaredClasses()) {
            String name = nested.getSimpleName().toLowerCase();
            assertFalse(name.contains("refund"));
            assertFalse(name.equals("serve"));
        }
    }

    private static List<String> names(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

}
