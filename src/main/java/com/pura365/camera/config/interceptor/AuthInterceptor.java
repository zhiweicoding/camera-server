package com.pura365.camera.config.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pura365.camera.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单 JWT 认证拦截器：
 * - 从 Authorization: Bearer <token> 解析用户
 * - 将 userId 放到 request attribute "currentUserId"
 * - 对部分无需登录的接口放行（设备/调试等）
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 白名单：不需要登录的接口（设备上报、调试、登录本身等）
        if (isPublicPath(path)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少 Authorization 头");
            return false;
        }
        String token = authHeader.substring(7);
        Long userId = authService.parseUserIdFromAccessToken(token);
        if (userId == null) {
            writeUnauthorized(response, "无效的或过期的 token");
            return false;
        }
        request.setAttribute("currentUserId", userId);
        return true;
    }

    private boolean isPublicPath(String path) {
        if (path == null) {
            return true;
        }
        // 登录与认证相关
        if (path.startsWith("/api/app/auth")) {
            return true;
        }
        // 设备 HTTP 接口
        if (path.equals("/get_time") || path.equals("/get_info") || path.equals("/resetdevice") || path.equals("/send_msg")) {
            return true;
        }
        // MQTT / WebRTC / DataChannel 调试接口（含 /api/internal/... 前缀）
        if (path.startsWith("/api/mqtt") || path.startsWith("/api/webrtc")
                || path.startsWith("/api/internal/mqtt")
                || path.startsWith("/api/internal/webrtc")
                || path.startsWith("/api/internal/datachannel")) {
            return true;
        }
        // 静态资源、文档、上传文件、预览图、错误页
        if (path.startsWith("/static/") || path.startsWith("/docs/") || path.startsWith("/pics/") || path.startsWith("/webrtc-demo") || path.startsWith("/uploads/") || path.startsWith("/previews/")) {
            return true;
        }
        // 机身号工具页面 & 接口放行
        if (path.equals("/device-id-tool.html") || path.startsWith("/api/device-id")) {
            return true;
        }
        // 国家电网演示页面
        if (path.equals("/sgcc.html")) {
            return true;
        }
        // 消息中心页面和接口
        if (path.equals("/message-center.html") || path.startsWith("/api/message-center")) {
            return true;
        }
        // 支付回调接口（PayPal/微信/Apple等）
        if (path.startsWith("/api/payment/paypal/") || path.startsWith("/api/payment/wechat/")
                || path.startsWith("/api/callback/")) {
            return true;
        }
        if (path.startsWith("/error") || path.startsWith("/actuator")) {
            return true;
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", message != null ? message : "Unauthorized");
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}