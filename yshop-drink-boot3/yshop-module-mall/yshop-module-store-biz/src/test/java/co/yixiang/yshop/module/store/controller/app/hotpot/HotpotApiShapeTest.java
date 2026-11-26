package co.yixiang.yshop.module.store.controller.app.hotpot;

import co.yixiang.yshop.module.store.controller.admin.hotpot.AdminHotpotController;
import co.yixiang.yshop.module.store.controller.admin.hotpot.vo.AdminHotpotCommands;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.AppHotpotCommands;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;
import co.yixiang.yshop.module.store.service.hotpot.HotpotService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

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

    private static List<String> names(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }

}
