package com.pura365.camera.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 统一的地区路由开关。
 * app.region.override 优先级最高，可强制走国内或国外链路。
 */
@Service
public class RegionRoutingService {

    private static final String AUTO = "auto";

    @Value("${app.region.override:auto}")
    private String regionOverride;

    public String resolveRegion(String... regionCandidates) {
        String override = normalizeOverride(regionOverride);
        if (!AUTO.equals(override)) {
            return override;
        }

        if (regionCandidates != null) {
            for (String candidate : regionCandidates) {
                if (StringUtils.hasText(candidate)) {
                    return normalizeRegion(candidate);
                }
            }
        }

        return "cn";
    }

    public boolean isChina(String... regionCandidates) {
        String region = resolveRegion(regionCandidates);
        return region.equals("cn") || region.equals("china") || region.startsWith("cn-");
    }

    private String normalizeOverride(String value) {
        if (!StringUtils.hasText(value)) {
            return AUTO;
        }
        String normalized = value.trim().toLowerCase();
        if (AUTO.equals(normalized)) {
            return AUTO;
        }
        return normalizeRegion(normalized);
    }

    private String normalizeRegion(String value) {
        String normalized = value.trim().toLowerCase();
        if ("oversea".equals(normalized)
                || "overseas".equals(normalized)
                || "intl".equals(normalized)
                || "international".equals(normalized)
                || "global".equals(normalized)) {
            return "us";
        }
        return normalized;
    }
}
