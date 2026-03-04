package com.pura365.camera.controller.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.controller.JWTUtil;
import com.pura365.camera.model.GetInfoRequest;
import com.pura365.camera.model.GetInfoResponse;
import com.pura365.camera.model.ResetDeviceRequest;
import com.pura365.camera.model.ResetDeviceResponse;
import com.pura365.camera.model.SendMsgRequest;
import com.pura365.camera.service.CameraService;
import com.pura365.camera.service.MqttMessageService;
import com.pura365.camera.util.TimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "摄像头内部接口", description = "摄像头设备内部调用接口")
@RestController
@RequestMapping("/")
public class CameraController {

    private static final Logger log = LoggerFactory.getLogger(CameraController.class);

    @Autowired
    private CameraService cameraService;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired(required = false)
    private MqttMessageService mqttMessageService;

    /**
     * 获取时间戳接口 - 不需要 JWT 验证，直接返回时间戳（秒）
     */
    @Operation(summary = "获取时间戳", description = "设备获取服务器当前 UTC 时间戳（秒）")
    @PostMapping(value = "/get_time")
    public ResponseEntity<String> getTime() {
        log.info("=== 摄像头 get_time 接口被调用 ===");
        long utcTimestamp = System.currentTimeMillis() / 1000;
        log.info("返回摄像头 UTC 时间: {}", utcTimestamp);
        return ResponseEntity.ok(String.valueOf(utcTimestamp));
    }

    /**
     * 获取设备信息接口
     */
    @Operation(summary = "获取设备信息", description = "摄像头通过加密 token 上报基本信息")
    @PostMapping(value = "/get_info",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getInfo(HttpServletRequest request) {
        log.info("=== /get_info 请求 ===");

        try {
            String rawBody = getRequestBody(request);
            log.info("请求体: {}", rawBody);

            String token = extractJwtToken(request, rawBody);
            if (token == null) {
                return ResponseEntity.badRequest().body("无法解析加密数据，请检查请求格式");
            }
            log.info("接收到的 token: {}", token);

            GetInfoRequest info = parseGetInfoRequestFromToken(token);
            log.info("解析出的请求参数: id={}, exp={}, mac={}, region={}, ssid={}",
                    info.getId(), info.getExp(), info.getMac(), info.getRegion(), info.getSsid());

            // 如果请求中包含 SSID，保存到 MQTT 服务（用于后续调试、配网等）
            if (info.getSsid() != null && !info.getSsid().isEmpty() && mqttMessageService != null) {
                mqttMessageService.registerDeviceSsid(info.getId(), info.getSsid());
                log.info("已保存设备 {} 的 SSID", info.getId());
            }

            if (info.getId() == null || info.getExp() == null || info.getMac() == null) {
                return ResponseEntity.badRequest().body("Missing required parameters");
            }

            // 验证时间戳
            if (!TimeValidator.isValid(info.getExp())) {
                return ResponseEntity.badRequest().body("Request expired");
            }

            GetInfoResponse response = cameraService.getDeviceInfo(info);
            if (response == null) {
                log.warn("/get_info 未返回设备配置，deviceId={}", info.getId());
                return ResponseEntity.badRequest().body("Device validation failed");
            }

            log.info("=== /get_info 响应 === {}", response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("/get_info 异常", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 从 HttpServletRequest 读取原始请求体
     */
    private String getRequestBody(HttpServletRequest request) {
        try {
            return request.getReader().lines().collect(Collectors.joining());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从原始请求中提取 token
     * 支持：
     *  1) 整个 body 即为 token 字符串（payload.signature 或 header.payload.signature）
     *  2) x-www-form-urlencoded 形式，例如 data=xxx 或 token=xxx
     */
    private String extractJwtToken(HttpServletRequest request, String rawBody) {
        if (rawBody == null) {
            return null;
        }
        String trimmed = rawBody.trim();

        // 情况1：整个 body 就是 token（没有等号，形如 payload.signature 或 header.payload.signature）
        if (!trimmed.contains("=") && trimmed.contains(".")) {
            String[] parts = trimmed.split("\\.");
            if (parts.length >= 2) {
                log.info("检测到整个请求体为 token 格式");
                return trimmed;
            }
        }

        // 情况2：form 表单: key=value&token=xxx
        if (rawBody.contains("=")) {
            String[] pairs = rawBody.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    try {
                        String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name());
                        if (value != null && value.contains(".")) {
                            String[] parts = value.split("\\.");
                            if (parts.length >= 2) {
                                log.info("从表单字段中检测到 token 格式, key={}", kv[0]);
                                return value;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 情况3：从参数中取常见字段
        String[] possibleParamNames = {"data", "token", "jwt", "payload", "encrypted_data"};
        for (String paramName : possibleParamNames) {
            String paramValue = request.getParameter(paramName);
            log.info("检查参数 '{}' : {}", paramName, (paramValue != null ? "存在" : "不存在"));
            if (paramValue != null && !paramValue.trim().isEmpty()) {
                String value = paramValue.trim();
                if (value.contains(".")) {
                    String[] parts = value.split("\\.");
                    if (parts.length >= 2) {
                        log.info("从参数 '{}' 获取到 token", paramName);
                        return value;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 解析自定义 token（payload.signature），payload 为 Base64(JSON)，支持无填充 Base64 字符串
     */
    private GetInfoRequest parseGetInfoRequestFromToken(String token) throws Exception {
        String[] parts = token.trim().split("\\.");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid token format");
        }

        // payload.signature 或 header.payload.signature
        String payloadPart = parts.length == 2 ? parts[0] : parts[Math.min(1, parts.length - 1)];

        // 补齐 Base64 填充
        int padding = (4 - payloadPart.length() % 4) % 4;
        for (int i = 0; i < padding; i++) {
            payloadPart += "=";
        }

        byte[] decoded = Base64.getDecoder().decode(payloadPart);
        String json = new String(decoded, StandardCharsets.UTF_8);
        log.info("解码后的 payload JSON: {}", json);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, GetInfoRequest.class);
    }

    /**
     * 测试 JWT token 生成接口（用于测试）
     */
    @Operation(summary = "生成测试 Token", description = "生成一个简单的 JWT token 供测试使用")
    @GetMapping("/test-token")
    public ResponseEntity<String> generateTestToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("test", "data");
        String token = jwtUtil.createToken(claims);
        return ResponseEntity.ok(token);
    }

    /**
     * 重置设备接口
     * 摄像头第一次配网连接后请求该 API，用于清除历史数据
     */
    @Operation(summary = "重置设备", description = "清除设备历史数据等初始化操作")
    @PostMapping(value = "/resetdevice",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resetDevice(HttpServletRequest request) {
        log.info("=== /resetdevice 请求 ===");

        try {
            String rawBody = getRequestBody(request);
            log.info("resetdevice请求体: {}", rawBody);

            String token = extractJwtToken(request, rawBody);
            if (token == null) {
                return ResponseEntity.badRequest().body("无法解析加密数据，请检查请求格式");
            }
            log.info("resetdevice接收到的 token: {}", token);

            ResetDeviceRequest resetRequest = parseResetDeviceRequestFromToken(token);
            log.info("resetdevice解析出的请求参数: id={}, exp={}, mac={}, ssid={}",
                    resetRequest.getId(), resetRequest.getExp(), resetRequest.getMac(), resetRequest.getSsid());

            // 如果请求中包含 SSID，保存到 MQTT 服务
            if (resetRequest.getSsid() != null && !resetRequest.getSsid().isEmpty() && mqttMessageService != null) {
                mqttMessageService.registerDeviceSsid(resetRequest.getId(), resetRequest.getSsid());
                log.info("resetdevice已保存设备 {} 的 SSID", resetRequest.getId());
            }

            if (resetRequest.getId() == null || resetRequest.getExp() == null || resetRequest.getMac() == null) {
                return ResponseEntity.badRequest().body("Missing required parameters");
            }

            // 验证时间戳
            if (!TimeValidator.isValid(resetRequest.getExp())) {
                return ResponseEntity.badRequest().body("Request expired");
            }

            int code = cameraService.resetDevice(resetRequest);
            ResetDeviceResponse response = new ResetDeviceResponse(code);

            log.info("=== /resetdevice 响应 === code={}", code);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("/resetdevice 异常", e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * 接收消息通知接口
     * 用于接收摄像头发送的事件信息或 AI 结果，不需要回复内容
     */
    @Operation(summary = "接收消息", description = "接收摄像头发送的事件或 AI 结果消息")
    @PostMapping(value = "/send_msg",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> sendMsg(HttpServletRequest request) {
        try {
            String rawBody = getRequestBody(request);
            String token = extractJwtToken(request, rawBody);
            if (token == null) {
                log.warn("[send_msg] 无法解析token，忽略该消息");
                return ResponseEntity.ok().build();
            }

            SendMsgRequest msgRequest = parseSendMsgRequestFromToken(token);
            boolean validExp = TimeValidator.isValid(msgRequest.getExp());
            log.info("[send_msg][diagnostic] id={}, topic={}, title={}, msg={}, exp={}, expValid={}, picurl={}, videourl={}",
                    msgRequest.getId(),
                    msgRequest.getTopic(),
                    msgRequest.getTitle(),
                    msgRequest.getMsg(),
                    msgRequest.getExp(),
                    validExp,
                    msgRequest.getPicurl(),
                    msgRequest.getVideourl());

            // 验证时间戳
            if (!validExp) {
                log.warn("[send_msg] 时间戳过期，忽略 - 设备={}, exp={}", msgRequest.getId(), msgRequest.getExp());
                return ResponseEntity.ok().build();
            }

            // 处理消息
            cameraService.handleMessage(msgRequest);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("[send_msg] 处理异常", e);
            return ResponseEntity.ok().build();
        }
    }

    /**
     * 解析 ResetDeviceRequest 的 token
     */
    private ResetDeviceRequest parseResetDeviceRequestFromToken(String token) throws Exception {
        String[] parts = token.trim().split("\\.");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid token format");
        }

        String payloadPart = parts.length == 2 ? parts[0] : parts[Math.min(1, parts.length - 1)];

        // 补齐 Base64 填充
        int padding = (4 - payloadPart.length() % 4) % 4;
        for (int i = 0; i < padding; i++) {
            payloadPart += "=";
        }

        byte[] decoded = Base64.getDecoder().decode(payloadPart);
        String json = new String(decoded, StandardCharsets.UTF_8);
        log.info("解码后的 payload JSON: {}", json);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, ResetDeviceRequest.class);
    }

    /**
     * 解析 SendMsgRequest 的 token
     */
    private SendMsgRequest parseSendMsgRequestFromToken(String token) throws Exception {
        String[] parts = token.trim().split("\\.");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid token format");
        }

        String payloadPart = parts.length == 2 ? parts[0] : parts[Math.min(1, parts.length - 1)];

        // 补齐 Base64 填充
        int padding = (4 - payloadPart.length() % 4) % 4;
        for (int i = 0; i < padding; i++) {
            payloadPart += "=";
        }

        byte[] decoded = Base64.getDecoder().decode(payloadPart);
        String json = new String(decoded, StandardCharsets.UTF_8);
        log.info("解码后的 payload JSON: {}", json);

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, SendMsgRequest.class);
    }
}
