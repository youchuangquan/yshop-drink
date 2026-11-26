package co.yixiang.yshop.module.store.controller.app.hotpot.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 顾客火锅写请求。取消只有 holdId，结账只有 sessionId 和 version。
 */
public final class AppHotpotCommands {

    private AppHotpotCommands() {
    }

    @Data
    public static class Reserve {

        @NotNull
        private Long shopId;

        @NotNull
        private Long slotId;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Cancel {

        @NotNull
        private Long holdId;

    }

    @Data
    public static class Seat {

        @NotNull
        private Long shopId;

        @NotNull
        private Long holdId;

        @NotNull
        private Long slotId;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class WalkIn {

        @NotNull
        private Long shopId;

        @NotNull
        private Long slotId;

        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Open {

        @NotNull
        private Long shopId;

        @NotNull
        private Long slotId;

        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class AddPot {

        @NotNull
        private Long shopId;

        @NotNull
        private Long sessionId;

        @NotNull
        private Long potId;

        @NotNull
        private Integer version;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class OrderDish {

        @NotNull
        private Long shopId;

        @NotNull
        private Long slotId;

        @NotBlank
        private String signCode;

        @NotNull
        private Long productId;

        @NotNull
        @Min(1)
        private Integer quantity;

        private Integer version;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Settle {

        @NotNull
        private Long sessionId;

        @NotNull
        private Integer version;

    }

}
