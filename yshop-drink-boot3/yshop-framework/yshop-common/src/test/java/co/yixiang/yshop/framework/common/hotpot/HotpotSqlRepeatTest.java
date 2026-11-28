package co.yixiang.yshop.framework.common.hotpot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotpotSqlRepeatTest {

    @Test
    void gapScriptsCheckExistenceBeforeAltering() throws IOException {
        String pageGap = text("hotpot-page-gap-v1.sql");
        String serveRefund = text("hotpot-serve-refund-v1.sql");
        String tenantSlot = text("order-request-tenant-slot-v1.sql");

        assertRepeatable(pageGap);
        assertRepeatable(serveRefund);
        assertTrue(pageGap.contains("CREATE TABLE IF NOT EXISTS `yshop_hotpot_session_dish`"));
        assertTrue(serveRefund.contains("chk_hotpot_dish_served_refunded"));
        assertTrue(serveRefund.contains("fees_waived"));
        assertFalse(pageGap.toLowerCase().contains("companion"));
        assertFalse(serveRefund.toLowerCase().contains("companion"));
        assertTrue(tenantSlot.contains("information_schema.COLUMNS"));
        assertTrue(tenantSlot.contains("PREPARE order_request_tenant_stmt"));
    }

    private static void assertRepeatable(String sql) {
        assertTrue(sql.contains("information_schema.COLUMNS"));
        assertTrue(sql.contains("COUNT(*) = 0"));
        assertTrue(sql.contains("DO 0"));
        assertTrue(sql.contains("PREPARE "));
        assertTrue(sql.contains("EXECUTE "));
        assertTrue(sql.contains("DEALLOCATE PREPARE "));
        assertFalse(sql.contains("\nALTER TABLE "));
    }

    private static String text(String name) throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            dir = dir.getParent();
        }
        throw new AssertionError("missing " + name);
    }

}
