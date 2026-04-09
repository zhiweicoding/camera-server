package com.pura365.camera.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.domain.Device;
import com.pura365.camera.domain.DeviceTrafficSim;
import com.pura365.camera.domain.UserDevice;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.enums.EnableStatus;
import com.pura365.camera.enums.SdCardStatus;
import com.pura365.camera.model.mqtt.*;
import com.pura365.camera.repository.DeviceRepository;
import com.pura365.camera.repository.DeviceTrafficSimRepository;
import com.pura365.camera.repository.UserDeviceRepository;
import com.pura365.camera.util.TimeValidator;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 消息服务
 * 负责发送消息到摄像头，以及处理摄像头上报的消息
 */
@Service
public class MqttMessageService {
    
    private static final Logger log = LoggerFactory.getLogger(MqttMessageService.class);
    
    @Value("${mqtt.broker.url:tcp://cam.pura365.cn:1883}")
    private String brokerUrl;

    @Value("${mqtt.cn.broker.url:tcp://cam.pura365.cn:1883}")
    private String chinaBrokerUrl;

    @Value("${mqtt.oversea.broker.url:tcp://sp.pura365.cn:1883}")
    private String overseaBrokerUrl;
    
    @Value("${mqtt.client.id:local-camera-server}")
    private String clientId;
    
    @Value("${mqtt.username:camera_test}")
    private String username;
    
    @Value("${mqtt.password:123456}")
    private String password;

    @Value("${device.probe.min-interval-ms:10000}")
    private long probeMinIntervalMs;

    @Value("${device.probe.offline-failure-threshold:2}")
    private int offlineFailureThreshold;

    @Value("${device.probe.response-timeout-ms:5000}")
    private long probeTimeoutMs;
    
    @Autowired
    private MqttEncryptService encryptService;
    
    @Autowired
    private DeviceSsidService deviceSsidService;
    
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceTrafficSimRepository deviceTrafficSimRepository;
    
    @Autowired
    private NetworkPairingStatusService pairingStatusService;

    @Autowired
    private RegionRoutingService regionRoutingService;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private MessageService messageService;
    
    // 缓存 WebRTC Offer：sid -> WebRtcMessage（最近一次）
    private final Map<String, WebRtcMessage> webrtcOfferCache = new ConcurrentHashMap<>();
    // 缓存 WebRTC Candidate：sid -> List<WebRtcMessage>（待拉取的远端 Candidate）
    private final Map<String, List<WebRtcMessage>> webrtcCandidateCache = new ConcurrentHashMap<>();
    // 等待设备响应的 Future：deviceId -> multiple waiters
    private final Map<String, List<CompletableFuture<Boolean>>> deviceInfoWaiters = new ConcurrentHashMap<>();
    // 等待灯泡配置响应的 Future
    private final Map<String, CompletableFuture<MqttBulbConfigMessage>> bulbConfigWaiters = new ConcurrentHashMap<>();
    // 正在等待CODE11响应的设备（用于3秒超时检测）
    private final Set<String> pendingProbeDevices = ConcurrentHashMap.newKeySet();
    // 最近一次发送探测的时间（用于节流，避免频繁刷新导致状态抖动）
    private final Map<String, Long> lastProbeTimestamps = new ConcurrentHashMap<>();
    // 连续探测失败次数，达到阈值后才判定离线
    private final Map<String, Integer> probeFailureCounts = new ConcurrentHashMap<>();
    // 延时任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private MqttClient mqttClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @PostConstruct
    public void init() {
        try {
            connectToMqtt();
            log.info("MQTT服务初始化成功");
        } catch (Exception e) {
            log.error("MQTT服务初始化失败", e);
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT连接已关闭");
            } catch (Exception e) {
                log.error("关闭MQTT连接失败", e);
            }
        }
    }
    
    /**
     * 连接到MQTT Broker
     */
    private void connectToMqtt() throws Exception {
        String activeBrokerUrl = resolveBrokerUrl();
        mqttClient = new MqttClient(activeBrokerUrl, clientId);
        
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        
        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }
        
        mqttClient.connect(options);
        log.info("已连接到MQTT Broker: {}, regionOverride={}",
                activeBrokerUrl,
                regionRoutingService != null ? regionRoutingService.getOverrideRegion() : "unknown");
        
        // 设置消息回调
        mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.warn("MQTT连接丢失", cause);
            }
            
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                handleIncomingMessage(topic, message.getPayload());
            }
            
            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                // 消息发送完成
            }
        });
        
        // 订阅所有设备主题（使用通配符）
        mqttClient.subscribe("camera/pura365/+/device", 0);
        log.info("已订阅主题: camera/pura365/+/device");
    }

    private String resolveBrokerUrl() {
        if (regionRoutingService != null && regionRoutingService.hasExplicitOverride()) {
            String overrideRegion = regionRoutingService.getOverrideRegion();
            return regionRoutingService.isChina(overrideRegion) ? chinaBrokerUrl : overseaBrokerUrl;
        }
        return brokerUrl;
    }
    
    /**
     * 处理收到的MQTT消息
     */
    private void handleIncomingMessage(String topic, byte[] payload) {
        try {
            log.info("收到MQTT消息 - Topic: {}, 长度: {} bytes", topic, payload.length);
            
            // 从 topic 提取设备序列号: camera/pura365/{deviceId}/device
            String deviceId = extractDeviceIdFromTopic(topic);
            if (deviceId == null) {
                log.error("无法从topic提取设备ID: {}", topic);
                return;
            }
            
            // 获取设备的SSID：优先使用Redis中的（最新的），Redis没有再用数据库的
            String ssid = deviceSsidService.getSsid(deviceId);
            if (ssid == null || ssid.isEmpty()) {
                // Redis中没有，从数据库获取
                Device device = deviceRepository.selectById(deviceId);
                if (device != null && device.getSsid() != null && !device.getSsid().isEmpty()) {
                    ssid = device.getSsid();
                }
            }
            
            if (ssid == null || ssid.isEmpty()) {
                log.error("设备 {} 的SSID为空（Redis和数据库都没有），跳过消息处理", deviceId);
                return;
            }
            
            // 解密消息
            String json = encryptService.decrypt(payload, ssid);
            //log.info("解密后的消息: {}", json);
            
            // 解析基础消息获取code
            MqttBaseMessage baseMsg = objectMapper.readValue(json, MqttBaseMessage.class);
            Integer code = baseMsg.getCode();
            
            if (code == null) {
                log.error("消息中没有code字段");
                return;
            }
            
            // 根据code分发处理
            handleMessageByCode(code, json, deviceId);
            
        } catch (Exception e) {
            log.error("处理MQTT消息失败", e);
        }
    }
    
    /**
     * 根据code分发消息处理
     */
    private void handleMessageByCode(Integer code, String json, String deviceId) throws Exception {
        log.info("处理设备 {} 的消息，CODE: {}", deviceId, code);
        
        switch (code) {
            case 138: // CODE 10 + 128: MQTT已连接
                MqttCode10Message code10 = objectMapper.readValue(json, MqttCode10Message.class);
                handleMqttConnected(code10, deviceId);
                break;
            case 139: // CODE 11 + 128: 设备信息响应
                MqttDeviceInfoMessage code11 = objectMapper.readValue(json, MqttDeviceInfoMessage.class);
                handleDeviceInfo(code11, deviceId);
                break;
            case 151: // CODE 23 + 128: WebRTC Offer响应
                WebRtcMessage offerMsg = objectMapper.readValue(json, WebRtcMessage.class);
                handleWebRtcOffer(offerMsg, deviceId);
                break;
            case 152: // CODE 24 + 128: WebRTC Answer响应
                WebRtcMessage answerMsg = objectMapper.readValue(json, WebRtcMessage.class);
                handleWebRtcAnswer(answerMsg, deviceId);
                break;
            case 153: // CODE 25 + 128: WebRTC Candidate响应
                WebRtcMessage candidateMsg = objectMapper.readValue(json, WebRtcMessage.class);
                handleWebRtcCandidate(candidateMsg, deviceId);
                break;
            case 159: // CODE 128 + 31: 摄像头发送的 ICE Candidate 更新
                WebRtcMessage candidate159Msg = objectMapper.readValue(json, WebRtcMessage.class);
                handleWebRtcCandidate159(candidate159Msg, deviceId);
                break;
            case 148: // CODE 20 + 128: 遗言消息（设备断开连接）
                handleDeviceWill(deviceId);
                break;
            case 147: // CODE 19 + 128: 固件升级状态响应
                MqttFirmwareUpdateStatusMessage firmwareMsg =
                        objectMapper.readValue(json, MqttFirmwareUpdateStatusMessage.class);
                handleFirmwareUpdateStatus(firmwareMsg, deviceId);
                break;
            case 157: // CODE 29 + 128: 灯泡配置设置响应
                MqttBulbConfigMessage code29Resp = objectMapper.readValue(json, MqttBulbConfigMessage.class);
                handleBulbConfigSetResponse(code29Resp, deviceId);
                break;
            case 158: // CODE 30 + 128: 灯泡配置查询响应
                MqttBulbConfigMessage code30Resp = objectMapper.readValue(json, MqttBulbConfigMessage.class);
                handleBulbConfigQueryResponse(code30Resp, deviceId);
                break;
            default:
                log.warn("未处理的消息CODE: {}", code);
        }
    }
    
    /**
     * 处理设备MQTT连接消息 (CODE 10)
     * 设备上线，标记为在线
     */
    private void handleMqttConnected(MqttCode10Message msg, String deviceId) {
        log.info("设备 {} MQTT已连接 - Status: {}", deviceId, msg.getStatus());
        
        // 设备上线，从pending列表移除
        onDeviceResponded(deviceId);
        
        // 更新设备在线状态到数据库（同时视为一次心跳）
        String deviceName = null;
        try {
            LocalDateTime now = LocalDateTime.now();
            Device device = deviceRepository.selectById(deviceId);
            if (device != null) {
                deviceName = device.getName();
                device.setStatus(DeviceOnlineStatus.ONLINE);
                device.setLastOnlineTime(now);
                device.setLastHeartbeatTime(now); // MQTT连接也视为心跳
                device.setUpdatedAt(now);
                deviceRepository.updateById(device);
                log.info("已更新设备 {} 状态为在线", deviceId);
            } else {
                // 设备不存在，创建新设备记录
                device = new Device();
                device.setId(msg.getUid());
                device.setMac("UNKNOWN"); // MAC地址后续通过设备信息更新
                device.setStatus(DeviceOnlineStatus.ONLINE);
                device.setEnabled(EnableStatus.ENABLED);
                device.setLastOnlineTime(now);
                device.setLastHeartbeatTime(now);
                device.setCreatedAt(now);
                device.setUpdatedAt(now);
                deviceRepository.insert(device);
                log.info("已创建新设备记录 {}", deviceId);
            }
        } catch (Exception e) {
            log.error("更新设备 {} 在线状态失败", deviceId, e);
        }

        completeDeviceInfoWaiters(deviceId, true);
        
        // 如果status=1（配网后首次连接），更新配网状态为成功
        if (msg.getStatus() != null && msg.getStatus() == 1) {
            log.info("设备 {} 配网后首次连接，更新配网状态为成功", deviceId);
            // 更新Redis中的配网状态为成功
            pairingStatusService.setSuccess(deviceId);
        }
        
        log.info("设备 {} 上线状态已更新，不再发送上线推送通知", deviceId);
    }
    
    /**
     * 处理设备信息响应(CODE 139 = CODE 11 + 128)
     * 更新设备的完整状态信息到数据库，同时作为心跳响应更新在线状态
     */
    private void handleDeviceInfo(MqttDeviceInfoMessage msg, String deviceId) {
        log.info("收到设备信息 - 设备: {}, WiFi: {}, RSSI: {}, 版本: {}, ICCID: {}, TF卡: {}",
                deviceId, msg.getWifiname(), msg.getWifirssi(), msg.getVer(), msg.getIccid(),
                msg.getSdstate() == 1 ? "有" : "无");
        
        // 设备响应了CODE11，从pending列表移除
        onDeviceResponded(deviceId);
        
        // 更新设备信息到数据库
        try {
            Device device = deviceRepository.selectById(deviceId);
            boolean isNewDevice = (device == null);
            
            if (isNewDevice) {
                // 设备不存在，创建新记录
                device = new Device();
                device.setId(deviceId);
                device.setMac("UNKNOWN");
                device.setEnabled(EnableStatus.ENABLED);
                device.setCreatedAt(LocalDateTime.now());
            }
            
            // 更新基本信息
            if (msg.getWifiname() != null) {
                device.setSsid(msg.getWifiname());
                // 同步更新 SSID 到缓存，用于后续消息加解密
                deviceSsidService.saveSsid(deviceId, msg.getWifiname());
            }
            if (msg.getVer() != null) {
                device.setFirmwareVersion(msg.getVer());
            }
            String iccid = null;
            if (msg.getIccid() != null && !msg.getIccid().trim().isEmpty()) {
                iccid = msg.getIccid().trim();
                device.setIccid(iccid);
            }
            
            // 更新WiFi信号强度
            if (msg.getWifirssi() != null) {
                device.setWifiRssi(msg.getWifirssi());
            }
            
            // 更新TF卡信息
            if (msg.getSdstate() != null) {
                device.setSdState(SdCardStatus.fromCode(msg.getSdstate()));
            }
            if (msg.getSdcap() != null) {
                device.setSdCapacity(msg.getSdcap());
            }
            if (msg.getSdblock() != null) {
                device.setSdBlockSize(msg.getSdblock());
            }
            if (msg.getSdfree() != null) {
                device.setSdFree(msg.getSdfree());
            }
            
            // 更新摄像头设置状态
            if (msg.getRotate() != null) {
                device.setRotate(msg.getRotate());
            }
            if (msg.getLightled() != null) {
                device.setLightLed(msg.getLightled());
            }
            if (msg.getWhiteled() != null) {
                device.setWhiteLed(msg.getWhiteled());
            }
            if (msg.getBulbsEn() != null) {
                device.setBulbsEn(msg.getBulbsEn());
            }
            
            // 更新在线状态和心跳时间
            device.setStatus(DeviceOnlineStatus.ONLINE);
            LocalDateTime now = LocalDateTime.now();
            device.setLastOnlineTime(now);
            device.setLastHeartbeatTime(now); // 收到设备信息即为心跳响应
            device.setUpdatedAt(now);
            
            if (isNewDevice) {
                deviceRepository.insert(device);
                log.info("已创建设备 {} 信息记录", deviceId);
            } else {
                deviceRepository.updateById(device);
                log.info("已更新设备 {} 状态: SSID={}, RSSI={}, 版本={}, ICCID={}, TF卡状态={}",
                        deviceId, msg.getWifiname(), msg.getWifirssi(), msg.getVer(), iccid, msg.getSdstate());
            }

            if (iccid != null) {
                syncTrafficSimMapping(deviceId, iccid);
            }
            
            // 通知等待者设备已响应
            completeDeviceInfoWaiters(deviceId, true);
            
            // 记录TF卡容量信息(用于调试)
            if (msg.getSdstate() != null && msg.getSdstate() == 1) {
                long totalBytes = (msg.getSdcap() != null && msg.getSdblock() != null) 
                        ? msg.getSdcap() * msg.getSdblock() : 0;
                long freeBytes = (msg.getSdfree() != null && msg.getSdblock() != null) 
                        ? msg.getSdfree() * msg.getSdblock() : 0;
                log.info("设备 {} TF卡容量: 总计 {} MB, 剩余 {} MB", 
                        deviceId, totalBytes / 1024 / 1024, freeBytes / 1024 / 1024);
            }
            
        } catch (Exception e) {
            log.error("更新设备 {} 信息失败", deviceId, e);
        }
    }

    private void syncTrafficSimMapping(String deviceId, String iccid) {
        try {
            LambdaQueryWrapper<DeviceTrafficSim> query = new LambdaQueryWrapper<>();
            query.eq(DeviceTrafficSim::getDeviceId, deviceId);
            DeviceTrafficSim existing = deviceTrafficSimRepository.selectOne(query);

            Date now = new Date();
            if (existing == null) {
                DeviceTrafficSim mapping = new DeviceTrafficSim();
                mapping.setDeviceId(deviceId);
                mapping.setSimId(iccid);
                mapping.setCreatedAt(now);
                mapping.setUpdatedAt(now);
                deviceTrafficSimRepository.insert(mapping);
                log.info("已同步设备4G SIM映射 - deviceId={}, simId={}, action=insert", deviceId, iccid);
                return;
            }

            boolean changed = existing.getSimId() == null || !iccid.equals(existing.getSimId().trim());
            existing.setSimId(iccid);
            existing.setUpdatedAt(now);
            deviceTrafficSimRepository.updateById(existing);
            log.info("已同步设备4G SIM映射 - deviceId={}, simId={}, action={}",
                    deviceId, iccid, changed ? "update" : "refresh");
        } catch (Exception e) {
            log.error("同步设备4G SIM映射失败 - deviceId={}, simId={}", deviceId, iccid, e);
        }
    }
    
    /**
     * 处理WebRTC Offer响应
     */
    private void handleWebRtcOffer(WebRtcMessage msg, String deviceId) {
        log.info("收到WebRTC Offer - SID: {}, Status: {}", msg.getSid(), msg.getStatus());
        // 缓存最新的 Offer，供调试/前端轮询使用
        if (msg.getSid() != null) {
            webrtcOfferCache.put(msg.getSid(), msg);
            log.info("已缓存 WebRTC Offer, SID: {}", msg.getSid());
        }
        // 通过 WebSocket 转发 Offer 给对应的客户端
        notifyWebRtcMessage(deviceId, "offer", msg);
    }
    
    /**
     * 处理WebRTC Answer响应
     */
    private void handleWebRtcAnswer(WebRtcMessage msg, String deviceId) {
        log.info("收到WebRTC Answer - SID: {}, Status: {}", msg.getSid(), msg.getStatus());
        // 通过 WebSocket 转发 Answer 给对应的客户端
        notifyWebRtcMessage(deviceId, "answer", msg);
    }
    
    /**
     * 处理WebRTC Candidate响应
     */
    private void handleWebRtcCandidate(WebRtcMessage msg, String deviceId) {
        log.info("收到WebRTC Candidate - SID: {}, Status: {}", msg.getSid(), msg.getStatus());
        if (msg.getSid() != null) {
            webrtcCandidateCache
                    .computeIfAbsent(msg.getSid(), k -> new CopyOnWriteArrayList<>())
                    .add(msg);
            log.info("已缓存 WebRTC Candidate, SID: {}", msg.getSid());
        }
        // 通过 WebSocket 转发 Candidate 给对应的客户端
        notifyWebRtcMessage(deviceId, "candidate", msg);
    }
    
    /**
     * 处理摄像头发送的 ICE Candidate 更新 (CODE 159 = 128+31)
     * 摄像头在收到 Answer 后发送自己的 ICE Candidate 给 App
     */
    private void handleWebRtcCandidate159(WebRtcMessage msg, String deviceId) {
        log.info("收到摄像头 ICE Candidate (CODE 159) - SID: {}, Candidate: {}", 
                msg.getSid(), msg.getCandidate());
        
        // 缓存候选，供 App 轮询
        if (msg.getSid() != null) {
            webrtcCandidateCache
                    .computeIfAbsent(msg.getSid(), k -> new CopyOnWriteArrayList<>())
                    .add(msg);
            log.info("已缓存摄像头 ICE Candidate (CODE 159), SID: {}", msg.getSid());
        }
        
        // 通过 WebSocket 转发给 App
        notifyWebRtcMessage(deviceId, "candidate", msg);
    }
    
    /**
     * 处理设备遗言消息(CODE 148)
     * 设备断开MQTT连接时由Broker发布，立即标记设备离线
     */
    private void handleDeviceWill(String deviceId) {
        pendingProbeDevices.remove(deviceId);
        log.info("收到设备 {} 遗言消息，立即标记为离线", deviceId);
        markDeviceOffline(deviceId);
    }

    /**
     * 处理固件升级状态响应(CODE 147)
     * status=0 升级失败
     * status=1 开始升级，设备随后会自动重启
     */
    private void handleFirmwareUpdateStatus(MqttFirmwareUpdateStatusMessage msg, String deviceId) {
        log.info("收到设备 {} 固件升级状态响应 - uid={}, status={}",
                deviceId, msg.getUid(), msg.getStatus());

        touchDeviceHeartbeat(deviceId);

        if (msg.getStatus() != null && msg.getStatus() == 0) {
            notifyFirmwareUpdateFailed(deviceId);
        }
    }

    private void touchDeviceHeartbeat(String deviceId) {
        try {
            Device device = deviceRepository.selectById(deviceId);
            if (device == null) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            device.setStatus(DeviceOnlineStatus.ONLINE);
            device.setLastOnlineTime(now);
            device.setLastHeartbeatTime(now);
            device.setUpdatedAt(now);
            deviceRepository.updateById(device);
        } catch (Exception e) {
            log.error("更新设备 {} 心跳时间失败", deviceId, e);
        }
    }

    private void notifyFirmwareUpdateFailed(String deviceId) {
        try {
            LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserDevice::getDeviceId, deviceId);
            List<UserDevice> relations = userDeviceRepository.selectList(wrapper);
            if (relations == null || relations.isEmpty()) {
                return;
            }

            for (UserDevice relation : relations) {
                if (relation.getUserId() == null) {
                    continue;
                }
                messageService.createMessageAndPush(
                        relation.getUserId(),
                        deviceId,
                        "event",
                        "固件升级失败",
                        "设备固件升级失败，请稍后重试",
                        null,
                        null,
                        true
                );
            }
        } catch (Exception e) {
            log.error("发送设备 {} 固件升级失败通知失败", deviceId, e);
        }
    }
    
    /**
     * 处理灯泡配置设置响应(CODE 157)
     */
    private void handleBulbConfigSetResponse(MqttBulbConfigMessage msg, String deviceId) {
        log.info("收到设备 {} 灯泡配置设置响应 - Status: {}", deviceId, msg.getStatus());
        
        if (msg.getStatus() != null && msg.getStatus() == 1) {
            log.info("设备 {} 灯泡配置设置成功", deviceId);
        } else {
            log.warn("设备 {} 灯泡配置设置失败", deviceId);
        }
        
        // 通知等待者
        CompletableFuture<MqttBulbConfigMessage> waiter = bulbConfigWaiters.remove(deviceId);
        if (waiter != null) {
            waiter.complete(msg);
        }
    }
    
    /**
     * 处理灯泡配置查询响应(CODE 158)
     * 更新设备的灯泡配置到数据库
     */
    private void handleBulbConfigQueryResponse(MqttBulbConfigMessage msg, String deviceId) {
        log.info("收到设备 {} 灯泡配置查询响应 - Status: {}, Detect: {}, Brightness: {}", 
                deviceId, msg.getStatus(), msg.getDetect(), msg.getBrightness());
        
        if (msg.getStatus() != null && msg.getStatus() == 1) {
            try {
                Device device = deviceRepository.selectById(deviceId);
                if (device != null) {
                    // 更新灯泡配置字段
                    device.setBulbDetect(msg.getDetect());
                    device.setBulbBrightness(msg.getBrightness());
                    device.setBulbEnable(msg.getEnable());
                    device.setBulbTimeOn1(msg.getTimeOn1());
                    device.setBulbTimeOff1(msg.getTimeOff1());
                    device.setBulbTimeOn2(msg.getTimeOn2());
                    device.setBulbTimeOff2(msg.getTimeOff2());
                    device.setUpdatedAt(LocalDateTime.now());
                    deviceRepository.updateById(device);
                    log.info("已更新设备 {} 灯泡配置到数据库", deviceId);
                }
            } catch (Exception e) {
                log.error("更新设备 {} 灯泡配置失败", deviceId, e);
            }
        }
        
        // 通知等待者
        CompletableFuture<MqttBulbConfigMessage> waiter = bulbConfigWaiters.remove(deviceId);
        if (waiter != null) {
            waiter.complete(msg);
        }
    }
    
    // ==================== WebSocket 通知相关 ====================
    
    // WebSocket 会话管理：deviceId -> List<WebSocketSession>
    // 实际项目中应使用 WebSocketHandler 管理，这里提供简化的回调接口
    private final Map<String, List<WebRtcMessageListener>> webrtcListeners = new ConcurrentHashMap<>();
    
    /**
     * WebRTC 消息监听器接口
     */
    public interface WebRtcMessageListener {
        void onMessage(String deviceId, String type, WebRtcMessage message);
    }
    
    /**
     * 注册 WebRTC 消息监听器
     */
    public void addWebRtcListener(String deviceId, WebRtcMessageListener listener) {
        webrtcListeners.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("已注册设备 {} 的 WebRTC 监听器", deviceId);
    }
    
    /**
     * 移除 WebRTC 消息监听器
     */
    public void removeWebRtcListener(String deviceId, WebRtcMessageListener listener) {
        List<WebRtcMessageListener> listeners = webrtcListeners.get(deviceId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                webrtcListeners.remove(deviceId);
            }
        }
    }
    
    /**
     * 通知 WebRTC 消息给监听者
     */
    private void notifyWebRtcMessage(String deviceId, String type, WebRtcMessage msg) {
        List<WebRtcMessageListener> listeners = webrtcListeners.get(deviceId);
        if (listeners != null && !listeners.isEmpty()) {
            for (WebRtcMessageListener listener : listeners) {
                try {
                    listener.onMessage(deviceId, type, msg);
                } catch (Exception e) {
                    log.error("通知 WebRTC 消息失败", e);
                }
            }
            log.info("已通知 {} 个监听者，设备: {}, 类型: {}", listeners.size(), deviceId, type);
        } else {
            log.info("设备 {} 没有注册监听器，消息类型: {}", deviceId, type);
        }
    }
    
    // ==================== 设备在线探测 ====================
    
    /**
     * 异步探测多个设备的在线状态
     * 发送CODE11后异步等待响应，避免列表查询时阻塞接口返回
     * 
     * @param deviceIds 设备ID列表
     */
    public void probeDevicesAsync(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        
        for (String deviceId : deviceIds) {
            probeDeviceAsync(deviceId);
        }
    }
    
    /**
     * 异步探测单个设备的在线状态
     * 发送CODE11后，在配置的超时时间内无响应记一次失败；连续失败达到阈值才标记离线
     * 
     * @param deviceId 设备ID
     */
    public void probeDeviceAsync(String deviceId) {
        if (deviceId == null) {
            return;
        }
        
        // 检查设备是否有效（ssid不为空）
        Device device = deviceRepository.selectById(deviceId);
        if (device == null || device.getSsid() == null || device.getSsid().isEmpty()) {
            log.debug("设备 {} ssid为空，跳过探测", deviceId);
            return;
        }
        
        // 如果设备已经在等待响应中，跳过重复探测
        if (pendingProbeDevices.contains(deviceId)) {
            log.debug("设备 {} 已在探测中，跳过", deviceId);
            return;
        }

        if (isProbeCoolingDown(deviceId)) {
            log.debug("设备 {} 距离上次探测不足 {}ms，跳过本次重复探测", deviceId, probeMinIntervalMs);
            return;
        }
        
        // 标记设备正在等待响应
        pendingProbeDevices.add(deviceId);
        lastProbeTimestamps.put(deviceId, System.currentTimeMillis());
        
        try {
            // 发送CODE11探测
            requestDeviceInfo(deviceId);
            log.info("已发送CODE11探测到设备 {}", deviceId);
            
            // 超时后检查是否收到响应
            scheduler.schedule(() -> {
                if (pendingProbeDevices.remove(deviceId)) {
                    handleProbeTimeout(deviceId, "列表探测");
                }
            }, probeTimeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            pendingProbeDevices.remove(deviceId);
            log.error("发送CODE11探测失败 - deviceId={}", deviceId, e);
        }
    }
    
    /**
     * 设备响应了CODE11（或CODE10上线），从等待列表移除
     * 在处理CODE10、CODE11响应时调用
     */
    private void onDeviceResponded(String deviceId) {
        pendingProbeDevices.remove(deviceId);
        probeFailureCounts.remove(deviceId);
    }

    /**
     * 处理一次探测超时。
     * 直接累计失败次数，达到阈值后再标记离线。
     */
    public void handleProbeTimeout(String deviceId, String source) {
        int threshold = getOfflineFailureThreshold();
        int failureCount = probeFailureCounts.merge(deviceId, 1, Integer::sum);
        if (failureCount > threshold) {
            failureCount = threshold;
            probeFailureCounts.put(deviceId, threshold);
        }

        if (failureCount < threshold) {
            log.info("设备 {} {}超时，连续失败 {}/{}，暂不标记离线", deviceId, source, failureCount, threshold);
            return;
        }

        log.info("设备 {} {}超时，连续失败 {}/{}，标记为离线", deviceId, source, failureCount, threshold);
        markDeviceOffline(deviceId);
    }
    
    // ==================== 设备离线检测 ====================
    
    /**
     * 标记设备离线。
     * 触发场景：1. 收到CODE20遗言  2. 发送CODE11后3秒内无响应
     */
    public void markDeviceOffline(String deviceId) {
        try {
            Device device = deviceRepository.selectById(deviceId);
            if (device == null) {
                log.warn("markDeviceOffline skipped, device not found: {}", deviceId);
                return;
            }
            if (device.getStatus() == DeviceOnlineStatus.ONLINE) {
                device.setStatus(DeviceOnlineStatus.OFFLINE);
                device.setUpdatedAt(LocalDateTime.now());
                deviceRepository.updateById(device);
                log.info("已标记设备 {} 为离线", deviceId);
            } else {
                log.info("设备 {} 已经是离线状态，跳过重复离线通知逻辑", deviceId);
            }
        } catch (Exception e) {
            log.error("标记设备 {} 离线失败", deviceId, e);
        }
    }

    private boolean isProbeCoolingDown(String deviceId) {
        if (probeMinIntervalMs <= 0) {
            return false;
        }

        Long lastProbeAt = lastProbeTimestamps.get(deviceId);
        return lastProbeAt != null && (System.currentTimeMillis() - lastProbeAt) < probeMinIntervalMs;
    }

    private int getOfflineFailureThreshold() {
        return Math.max(offlineFailureThreshold, 1);
    }
    
    /**
     * 发送消息到指定设备（指定SSID，可为null）
     */
    public void sendToDevice(String deviceId, Object message, String ssid) throws Exception {
        String topic = "camera/pura365/" + deviceId + "/master";

        // 打印未加密前的 JSON（方便对照设备侧日志）
        try {
            String jsonPlain = objectMapper.writeValueAsString(message);
            log.info("即将发送MQTT消息到设备 {} - Topic: {}, 明文JSON: {}", deviceId, topic, jsonPlain);
        } catch (Exception e) {
            log.warn("序列化MQTT消息为JSON失败，将继续发送加密消息", e);
        }
        Device device = deviceRepository.selectById(deviceId);
        if (device != null) {
            ssid = device.getSsid();
        }
        // 加密消息
        byte[] encrypted = encryptService.encrypt(message, ssid);
        
        // 发送
        MqttMessage mqttMessage = new MqttMessage(encrypted);
        mqttMessage.setQos(1);
        mqttMessage.setRetained(false);
        
        mqttClient.publish(topic, mqttMessage);
        log.info("已发送消息到设备 {} - Topic: {}, 大小: {} bytes", deviceId, topic, encrypted.length);
    }
    
    /**
     * 请求设备信息（CODE 11），ssid 现在走默认配置
     */
    public void requestDeviceInfo(String deviceId) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 11);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        sendToDevice(deviceId, msg, null);
        log.info("已请求设备 {} 的信息", deviceId);
    }
    
    /**
     * 请求设备信息并等待响应（CODE 11）
     * 
     * @param deviceId 设备ID
     * @param timeoutMs 超时时间（毫秒）
     * @return true=设备已响应，false=超时或失败
     */
    public boolean requestDeviceInfoAndWait(String deviceId, long timeoutMs) {
        CompletableFuture<Boolean> future = registerDeviceInfoWaiter(deviceId);
        try {
            // 发送请求
            requestDeviceInfo(deviceId);
            
            // 等待响应
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("设备 {} 响应超时或失败: {}", deviceId, e.getMessage());
            return false;
        } finally {
            removeDeviceInfoWaiter(deviceId, future);
        }
    }

    /**
     * 为多个设备发送CODE11并并发等待响应。
     * 主要用于手动刷新时短等当前离线设备，让本次列表返回更接近实时状态。
     */
    public Map<String, Boolean> requestDeviceInfoBatchAndWait(List<String> deviceIds, long timeoutMs) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, CompletableFuture<Boolean>> futures = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 0L);

        for (String deviceId : deviceIds) {
            if (!isProbeableDevice(deviceId)) {
                continue;
            }

            CompletableFuture<Boolean> future = registerDeviceInfoWaiter(deviceId);
            futures.put(deviceId, future);

            if (pendingProbeDevices.contains(deviceId)) {
                log.debug("设备 {} 已有探测进行中，手动刷新直接等待已有结果", deviceId);
                continue;
            }

            pendingProbeDevices.add(deviceId);
            lastProbeTimestamps.put(deviceId, System.currentTimeMillis());

            try {
                requestDeviceInfo(deviceId);
                log.info("手动刷新已发送CODE11探测到设备 {}", deviceId);
                scheduler.schedule(() -> {
                    if (pendingProbeDevices.remove(deviceId)) {
                        handleProbeTimeout(deviceId, "手动刷新");
                    }
                }, probeTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                pendingProbeDevices.remove(deviceId);
                log.error("手动刷新发送CODE11探测失败 - deviceId={}", deviceId, e);
                future.complete(false);
            }
        }

        Map<String, Boolean> result = new LinkedHashMap<>();
        for (Map.Entry<String, CompletableFuture<Boolean>> entry : futures.entrySet()) {
            String deviceId = entry.getKey();
            CompletableFuture<Boolean> future = entry.getValue();

            try {
                long remainingMs = deadline - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    result.put(deviceId, false);
                } else {
                    result.put(deviceId, future.get(remainingMs, TimeUnit.MILLISECONDS));
                }
            } catch (Exception e) {
                log.debug("手动刷新等待设备 {} 响应超时或失败: {}", deviceId, e.getMessage());
                result.put(deviceId, false);
            } finally {
                removeDeviceInfoWaiter(deviceId, future);
            }
        }

        return result;
    }

    private CompletableFuture<Boolean> registerDeviceInfoWaiter(String deviceId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        deviceInfoWaiters.compute(deviceId, (key, waiters) -> {
            List<CompletableFuture<Boolean>> nextWaiters =
                    waiters != null ? waiters : new CopyOnWriteArrayList<>();
            nextWaiters.add(future);
            return nextWaiters;
        });
        return future;
    }

    private void removeDeviceInfoWaiter(String deviceId, CompletableFuture<Boolean> future) {
        deviceInfoWaiters.computeIfPresent(deviceId, (key, waiters) -> {
            waiters.remove(future);
            return waiters.isEmpty() ? null : waiters;
        });
    }

    private void completeDeviceInfoWaiters(String deviceId, boolean result) {
        List<CompletableFuture<Boolean>> waiters = deviceInfoWaiters.remove(deviceId);
        if (waiters == null || waiters.isEmpty()) {
            return;
        }

        for (CompletableFuture<Boolean> waiter : waiters) {
            waiter.complete(result);
        }
        log.debug("设备 {} 响应已通知 {} 个等待者", deviceId, waiters.size());
    }

    private boolean isProbeableDevice(String deviceId) {
        if (deviceId == null) {
            return false;
        }

        Device device = deviceRepository.selectById(deviceId);
        if (device == null || device.getSsid() == null || device.getSsid().isEmpty()) {
            log.debug("设备 {} ssid为空，跳过等待探测", deviceId);
            return false;
        }
        return true;
    }
    
    /**
     * 请求WebRTC Offer（CODE 23），ssid 走默认
     */
    public void requestWebRtcOffer(String deviceId, String sid, String rtcServer) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 23);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("sid", sid);
        msg.put("rtc", rtcServer); // 格式: server,user,pass
        
        sendToDevice(deviceId, msg, null);
        log.info("已请求设备 {} 的WebRTC Offer", deviceId);
    }
    
    /**
     * 发送WebRTC Answer（CODE 24），ssid 走默认
     */
    public void sendWebRtcAnswer(String deviceId, String sid, String sdp) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 24);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("sid", sid);
        msg.put("sdp", sdp);
        
        sendToDevice(deviceId, msg, null);
        log.info("已发送WebRTC Answer到设备 {}", deviceId);
    }
    
    /**
     * 发送WebRTC Candidate（CODE 25），ssid 走默认
     */
    public void sendWebRtcCandidate(String deviceId, String sid, String candidate) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 25);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("sid", sid);
        msg.put("candidate", candidate);
        
        sendToDevice(deviceId, msg, null);
        log.info("已发送WebRTC Candidate到设备 {}", deviceId);
    }
    
    /**
     * 发送WebRTC Candidate更新（CODE 159 = 128+31）
     * 用于 Answer 发送后更新 Offer 端的 Candidate 信息
     * 
     * @param deviceId 设备ID
     * @param sid Peer ID，用于区分连接的设备
     * @param candidate Candidate 内容（仅 IPv4）
     */
    public void sendWebRtcCandidate159(String deviceId, String sid, String candidate) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 159);  // 128 + 31
        msg.put("uide", deviceId);  // 摄像头序列号
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("sid", sid);
        msg.put("candidate", candidate);
        
        sendToDevice(deviceId, msg, null);
        log.info("已发送WebRTC Candidate159到设备 {} - SID: {}", deviceId, sid);
    }

    /**
     * 发送固件升级命令（CODE 19）
     */
    public void sendFirmwareUpdateCommand(String deviceId, String version, String path, String md5) throws Exception {
        Map<String, Object> msg = new HashMap<>();
        msg.put("code", 19);
        msg.put("time", TimeValidator.getCurrentTimestamp());
        msg.put("versoin", version);
        msg.put("path", path);
        msg.put("md5", md5);

        sendToDevice(deviceId, msg, null);
        log.info("已发送固件升级命令到设备 {} - version={}, path={}", deviceId, version, path);
    }
    
    /**
     * 获取最新的 WebRTC Offer（按 sid），不删除缓存
     */
    public WebRtcMessage getLatestOffer(String sid) {
        return webrtcOfferCache.get(sid);
    }
    
    /**
     * 获取并删除 WebRTC Offer（按 sid）
     * 用于 App 轮询获取 Offer 后清除缓存，防止重复获取
     */
    public WebRtcMessage consumeOffer(String sid) {
        return webrtcOfferCache.remove(sid);
    }
    
    /**
     * 获取并清空指定 sid 下缓存的 WebRTC Candidates
     */
    public List<WebRtcMessage> drainCandidates(String sid) {
        return webrtcCandidateCache.remove(sid);
    }
    
    /**
     * 获取指定 sid 下缓存的 WebRTC Candidates（只读取不删除）
     * App 端通过 _processedCandidates 去重，避免重复处理
     */
    public List<WebRtcMessage> peekCandidates(String sid) {
        return webrtcCandidateCache.get(sid);
    }
    
    /**
     * 设置灯泡配置并等待响应（CODE 29）
     * 
     * @param deviceId 设备ID
     * @param config 灯泡配置参数
     * @param timeoutMs 超时时间（毫秒）
     * @return 设备响应消息，超时返回 null
     */
    public MqttBulbConfigMessage setBulbConfigAndWait(String deviceId, Map<String, Object> config, long timeoutMs) {
        try {
            CompletableFuture<MqttBulbConfigMessage> future = new CompletableFuture<>();
            bulbConfigWaiters.put(deviceId, future);
            
            // 发送配置请求
            Map<String, Object> msg = new HashMap<>(config);
            msg.put("code", 29);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            sendToDevice(deviceId, msg, null);
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("设备 {} 灯泡配置响应超时或失败: {}", deviceId, e.getMessage());
            bulbConfigWaiters.remove(deviceId);
            return null;
        }
    }
    
    /**
     * 获取灯泡配置并等待响应（CODE 30）
     * 
     * @param deviceId 设备ID
     * @param timeoutMs 超时时间（毫秒）
     * @return 设备响应消息，超时返回 null
     */
    public MqttBulbConfigMessage getBulbConfigAndWait(String deviceId, long timeoutMs) {
        try {
            CompletableFuture<MqttBulbConfigMessage> future = new CompletableFuture<>();
            bulbConfigWaiters.put(deviceId, future);
            
            // 发送查询请求
            Map<String, Object> msg = new HashMap<>();
            msg.put("code", 30);
            msg.put("time", TimeValidator.getCurrentTimestamp());
            sendToDevice(deviceId, msg, null);
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.debug("设备 {} 灯泡配置查询响应超时或失败: {}", deviceId, e.getMessage());
            bulbConfigWaiters.remove(deviceId);
            return null;
        }
    }
    
    /**
     * 从 topic 提取设备ID
     */
    private String extractDeviceIdFromTopic(String topic) {
        // camera/pura365/{deviceId}/device
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return null;
    }
    
    /**
     * 注册设备的SSID（用于加解密）
     */
    public void registerDeviceSsid(String deviceId, String ssid) {
        deviceSsidService.saveSsid(deviceId, ssid);
    }

}
