package com.pura365.camera.controller.internal;

import com.pura365.camera.model.ApiResponse;
import com.pura365.camera.service.LinksFieldTrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 4G SIM flow query test endpoints (temporary for Postman verification).
 */
@Tag(name = "4G流量测试", description = "用于联调 LinksField 4G 流量接口的临时测试接口")
@RestController
@RequestMapping("/api/internal/mqtt/traffic-test")
public class TrafficTestController {

    private static final Logger log = LoggerFactory.getLogger(TrafficTestController.class);

    /**
     * Default SIM card id provided by card vendor.
     */
    private static final String DEFAULT_SIM_ID = "898608331925C0358233";

    @Autowired
    private LinksFieldTrafficService linksFieldTrafficService;

    @Operation(summary = "测试查询SIM剩余流量(POST)")
    @PostMapping("/remaining-data")
    public ApiResponse<Map<String, Object>> testRemainingDataPost(
            @RequestBody(required = false) Map<String, Object> body) {
        String simId = resolveSimId(body == null ? null : body.get("sim_id"));
        return doQuery(simId);
    }

    @Operation(summary = "测试查询SIM剩余流量(GET)")
    @GetMapping("/remaining-data")
    public ApiResponse<Map<String, Object>> testRemainingDataGet(
            @RequestParam(value = "sim_id", required = false) String simId) {
        return doQuery(resolveSimId(simId));
    }

    private ApiResponse<Map<String, Object>> doQuery(String simId) {
        try {
            Map<String, Object> thirdResult = linksFieldTrafficService.queryRemainingData(simId);
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("sim_id", simId);
            result.put("raw", thirdResult);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("4G flow test query failed, simId={}", simId, e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    private String resolveSimId(Object simIdObj) {
        if (simIdObj == null) {
            return DEFAULT_SIM_ID;
        }
        String simId = String.valueOf(simIdObj).trim();
        return StringUtils.hasText(simId) ? simId : DEFAULT_SIM_ID;
    }
}
