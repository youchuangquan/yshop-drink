package co.yixiang.yshop.module.store.controller.app.hotpot;

import co.yixiang.yshop.framework.common.pojo.CommonResult;
import co.yixiang.yshop.framework.security.core.annotations.PreAuthenticated;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.AppHotpotCommands;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotBoundBillResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotHoldResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotScanResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSessionResp;
import co.yixiang.yshop.module.store.controller.app.hotpot.vo.HotpotSlotResp;
import co.yixiang.yshop.module.store.service.hotpot.HotpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static co.yixiang.yshop.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static co.yixiang.yshop.framework.common.exception.util.ServiceExceptionUtil.exception;
import static co.yixiang.yshop.framework.common.pojo.CommonResult.success;
import static co.yixiang.yshop.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 火锅顾客接口。当前小程序没有预约、开桌、扫码结账页面，这里不新增页面。
 * 也没有「我的预约」列表。
 */
@Tag(name = "用户 APP - 火锅")
@RestController
@RequestMapping("/hotpot")
@Validated
public class AppHotpotController {

    @Resource
    private HotpotService hotpotService;

    @GetMapping("/slot")
    @Operation(summary = "本店时段")
    @PreAuthenticated
    public CommonResult<List<HotpotSlotResp>> listSlots(@RequestParam("shopId") Long shopId) {
        return success(hotpotService.listSlots(shopId));
    }

    @GetMapping("/bill")
    @Operation(summary = "绑定顾客查看自己的开台")
    @PreAuthenticated
    public CommonResult<HotpotBoundBillResp> boundBill(@RequestParam("shopId") Long shopId,
                                                       @RequestParam("signCode") String signCode) {
        return success(hotpotService.boundBill(shopId, signCode, loginUser()));
    }

    @PostMapping("/reservation")
    @Operation(summary = "预约占时段容量")
    @PreAuthenticated
    public CommonResult<HotpotHoldResp> reserve(@Valid @RequestBody AppHotpotCommands.Reserve req) {
        return success(hotpotService.reserve(req.getShopId(), loginUser(), req.getSlotId(), req.getRequestKey()));
    }

    @PostMapping("/reservation/cancel")
    @Operation(summary = "预约人取消")
    @PreAuthenticated
    public CommonResult<HotpotHoldResp> cancel(@Valid @RequestBody AppHotpotCommands.Cancel req) {
        return success(hotpotService.cancel(loginUser(), req.getHoldId()));
    }

    @PostMapping("/seat")
    @Operation(summary = "入座已有预约")
    @PreAuthenticated
    public CommonResult<HotpotHoldResp> seat(@Valid @RequestBody AppHotpotCommands.Seat req) {
        return success(hotpotService.seat(req.getShopId(), req.getHoldId(), req.getSlotId(), loginUser(), false,
                req.getRequestKey()));
    }

    @PostMapping("/walk-in")
    @Operation(summary = "散客占时段")
    @PreAuthenticated
    public CommonResult<HotpotHoldResp> walkIn(@Valid @RequestBody AppHotpotCommands.WalkIn req) {
        return success(hotpotService.walkIn(req.getShopId(), req.getSlotId(), req.getSignCode(), loginUser(), false,
                req.getRequestKey()));
    }

    @PostMapping("/session/open")
    @Operation(summary = "开台")
    @PreAuthenticated
    public CommonResult<HotpotSessionResp> open(@Valid @RequestBody AppHotpotCommands.Open req) {
        return success(hotpotService.open(req.getShopId(), req.getSlotId(), req.getSignCode(), loginUser(), false,
                req.getRequestKey()));
    }

    @PostMapping("/session/pot")
    @Operation(summary = "按门店锅底加价")
    @PreAuthenticated
    public CommonResult<HotpotSessionResp> addPot(@Valid @RequestBody AppHotpotCommands.AddPot req) {
        return success(hotpotService.addPot(req.getShopId(), req.getSessionId(), req.getPotId(), req.getVersion(),
                loginUser(), false, req.getRequestKey()));
    }

    @PostMapping("/session/settle")
    @Operation(summary = "结账")
    @PreAuthenticated
    public CommonResult<HotpotSessionResp> settle(@Valid @RequestBody AppHotpotCommands.Settle req) {
        return success(hotpotService.settle(loginUser(), req.getSessionId(), req.getVersion(), false, null));
    }

    @GetMapping("/scan")
    @Operation(summary = "公开扫码，只返回签码和是否占用")
    public CommonResult<HotpotScanResp> scan(@RequestParam("shopId") Long shopId,
                                             @RequestParam("signCode") String signCode) {
        return success(hotpotService.scan(shopId, signCode));
    }

    private static long loginUser() {
        Long uid = getLoginUserId();
        if (uid == null || uid <= 0) {
            throw exception(UNAUTHORIZED);
        }
        return uid;
    }

}
