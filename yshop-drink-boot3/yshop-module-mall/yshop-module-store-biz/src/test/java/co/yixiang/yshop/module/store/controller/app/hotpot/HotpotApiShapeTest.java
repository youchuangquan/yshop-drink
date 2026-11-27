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
import co.yixiang.yshop.module.store.service.hotpot.HotpotService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertEquals(List.of("slotId", "shopId", "capacity", "occupied"), names(HotpotSlotResp.class));
        assertEquals(List.of("id", "slotId", "shopId", "uid", "status", "sessionId"), names(HotpotStaffHoldResp.class));
        assertEquals(List.of("sessionId"), names(HotpotBoundBillResp.class));
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

        Method holds = AdminHotpotController.class.getMethod("listHolds");
        assertNotNull(holds.getAnnotation(PreAuthorize.class));
        assertEquals("/hold", holds.getAnnotation(GetMapping.class).value()[0]);
        assertEquals(0, holds.getParameterCount());
    }

    private static List<String> names(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

}
