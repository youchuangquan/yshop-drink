package co.yixiang.yshop.module.store.controller.admin.hotpot;

import co.yixiang.yshop.framework.common.pojo.CommonResult;
import co.yixiang.yshop.framework.security.core.util.ShopSecurityUtils;
import co.yixiang.yshop.module.store.controller.admin.hotpot.vo.AdminHotpotCommands;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotStaffHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotPotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotTableResp;
import co.yixiang.yshop.module.store.service.hotpot.HotpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.framework.common.pojo.CommonResult.success;
import static co.yixiang.yshop.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 店员火锅配置和入座。不整表替换时段，也不提供代客声明实付金额的接口。
 */
@Tag(name = "管理后台 - 火锅")
@RestController
@RequestMapping("/store/hotpot")
@Validated
public class AdminHotpotController {

    @Resource
    private HotpotService hotpotService;

    @PostMapping("/slot")
    @Operation(summary = "新增一个时段容量")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotSlotResp> createSlot(@Valid @RequestBody AdminHotpotCommands.Slot req) {
        return success(hotpotService.createSlot(shopId(), loginUser(), req.getCapacity(), req.getStartTime(),
                req.getEndTime(), req.getRequestKey()));
    }

    @PostMapping("/table")
    @Operation(summary = "新增一个桌台签码")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotTableResp> createTable(@Valid @RequestBody AdminHotpotCommands.Table req) {
        return success(hotpotService.createTable(shopId(), loginUser(), req.getSignCode(), req.getRequestKey()));
    }

    @PostMapping("/pot")
    @Operation(summary = "新增一个锅底价格")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotPotResp> createPot(@Valid @RequestBody AdminHotpotCommands.Pot req) {
        return success(hotpotService.createPot(shopId(), loginUser(), req.getName(), req.getPriceFen(),
                req.getRequestKey()));
    }

    @GetMapping("/slot")
    @Operation(summary = "本店时段")
    @PreAuthorize("@ss.hasPermission('store:shop:query')")
    public CommonResult<List<HotpotSlotResp>> listSlots() {
        return success(hotpotService.listSlots(shopId()));
    }

    @GetMapping("/hold")
    @Operation(summary = "本店当前占位")
    @PreAuthorize("@ss.hasPermission('store:shop:query')")
    public CommonResult<List<HotpotStaffHoldResp>> listHolds() {
        return success(hotpotService.listCurrentHolds(shopId()));
    }

    @GetMapping("/table")
    @Operation(summary = "本店桌台")
    @PreAuthorize("@ss.hasPermission('store:shop:query')")
    public CommonResult<List<HotpotTableResp>> listTables() {
        return success(hotpotService.listTables(shopId()));
    }

    @GetMapping("/pot")
    @Operation(summary = "本店锅底")
    @PreAuthorize("@ss.hasPermission('store:shop:query')")
    public CommonResult<List<HotpotPotResp>> listPots() {
        return success(hotpotService.listPots(shopId()));
    }

    @PostMapping("/seat")
    @Operation(summary = "店员入座")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotHoldResp> seat(@Valid @RequestBody AdminHotpotCommands.Seat req) {
        return success(hotpotService.seat(shopId(), req.getHoldId(), req.getSlotId(), loginUser(), true,
                req.getRequestKey()));
    }

    @PostMapping("/walk-in")
    @Operation(summary = "店员登记散客")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotHoldResp> walkIn(@Valid @RequestBody AdminHotpotCommands.WalkIn req) {
        return success(hotpotService.walkIn(shopId(), req.getSlotId(), req.getSignCode(), 0L, true, req.getRequestKey()));
    }

    @PostMapping("/reservation/seat")
    @Operation(summary = "把预约安排到空桌并开出台次")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotSessionResp> seatReservation(@Valid @RequestBody AdminHotpotCommands.ReservationSeat req) {
        return success(hotpotService.seatReservation(shopId(), req.getHoldId(), req.getSlotId(), req.getSignCode(),
                req.getRequestKey()));
    }

    @PostMapping("/session/open")
    @Operation(summary = "店员为散客开台")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotSessionResp> open(@Valid @RequestBody AdminHotpotCommands.Open req) {
        return success(hotpotService.open(shopId(), req.getSlotId(), req.getSignCode(), 0L, true, req.getRequestKey()));
    }

    @PostMapping("/session/pot")
    @Operation(summary = "按本店锅底给开台加价")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotSessionResp> addPot(@Valid @RequestBody AdminHotpotCommands.AddPot req) {
        return success(hotpotService.addPot(shopId(), req.getSessionId(), req.getPotId(), req.getVersion(),
                loginUser(), true, req.getRequestKey()));
    }

    @PostMapping("/session/settle")
    @Operation(summary = "店员结账。不能填写实付金额")
    @PreAuthorize("@ss.hasPermission('store:shop:update')")
    public CommonResult<HotpotSessionResp> settle(@Valid @RequestBody AdminHotpotCommands.Settle req) {
        return success(hotpotService.settle(loginUser(), req.getSessionId(), req.getVersion(), true, shopId()));
    }

    private static long shopId() {
        return ShopSecurityUtils.currentStaffShop(null);
    }

    private static long loginUser() {
        Long uid = getLoginUserId();
        if (uid == null || uid <= 0) {
            throw exception(UNAUTHORIZED);
        }
        return uid;
    }

}
