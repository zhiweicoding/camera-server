package com.pura365.camera.controller.internal;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.pura365.camera.model.mqtt.WebRtcMessage;
import com.pura365.camera.service.MqttMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WebRTC 信令接口：给本地 Demo 页面和 APP 使用
 */
@Tag(name = "WebRTC信令", description = "WebRTC Offer/Candidate 查询接口")
@RestController
@RequestMapping("/api/internal/webrtc")
public class WebRtcController {

    private static final Logger log = LoggerFactory.getLogger(WebRtcController.class);

    private final MqttMessageService mqttMessageService;

    public WebRtcController(MqttMessageService mqttMessageService) {
        this.mqttMessageService = mqttMessageService;
    }

    /**
     *      * 获取指定 SID 对应的最新 WebRTC Offer（由设备通过 MQTT CODE 151 上报）
     */
    @GetMapping("/offer/{sid}")
    public ResponseEntity<Map<String, Object>> getOffer(@PathVariable String sid) {
        Map<String, Object> result = new HashMap<>();
        WebRtcMessage msg = mqttMessageService.getLatestOffer(sid);
        if (msg == null) {
            result.put("found", false);
            return ResponseEntity.ok(result);
        }
        result.put("found", true);
        result.put("sid", msg.getSid());
        result.put("sdp", msg.getSdp());
        result.put("status", msg.getStatus());
        result.put("time", msg.getTime());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定 SID 下缓存的 WebRTC Candidates（由设备上报，包括 Code 153 和 Code 159）
     * 使用 peek 而非 drain，避免候选丢失，App 端通过 _processedCandidates 去重
     */
    @GetMapping("/candidates/{sid}")
    public ResponseEntity<Map<String, Object>> getCandidates(@PathVariable String sid) {
        Map<String, Object> result = new HashMap<>();
        List<WebRtcMessage> list = mqttMessageService.peekCandidates(sid);
        if (list == null || list.isEmpty()) {
            result.put("found", false);
            result.put("candidates", new String[0]);
            return ResponseEntity.ok(result);
        }
        result.put("found", true);
        result.put("candidates", list.stream()
                .map(WebRtcMessage::getCandidate)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toList()));
        return ResponseEntity.ok(result);
    }
}
