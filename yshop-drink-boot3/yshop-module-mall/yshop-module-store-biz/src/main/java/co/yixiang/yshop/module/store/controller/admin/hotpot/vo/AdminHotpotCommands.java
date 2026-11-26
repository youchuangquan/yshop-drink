package co.yixiang.yshop.module.store.controller.admin.hotpot.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 店员火锅配置。一次只新增一条，不整表替换时段。
 */
public final class AdminHotpotCommands {

    private AdminHotpotCommands() {
    }

    @Data
    public static class Slot {

        @NotNull
        @Min(1)
        private Integer capacity;

        @NotBlank
        private String startTime;

        @NotBlank
        private String endTime;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Table {

        @NotBlank
        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Pot {

        @NotBlank
        private String name;

        @NotNull
        @Min(0)
        private Integer priceFen;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class ReservationSeat {

        @NotNull
        private Long holdId;

        @NotNull
        private Long slotId;

        @NotBlank
        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Seat {

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
        private Long slotId;

        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class Open {

        @NotNull
        private Long slotId;

        private String signCode;

        @NotBlank
        private String requestKey;

    }

    @Data
    public static class AddPot {

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
    public static class Settle {

        @NotNull
        private Long sessionId;

        @NotNull
        private Integer version;

    }

}
