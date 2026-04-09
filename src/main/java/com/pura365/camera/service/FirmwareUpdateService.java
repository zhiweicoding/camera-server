package com.pura365.camera.service;

import com.pura365.camera.config.StorageConfig;
import com.pura365.camera.domain.Device;
import com.pura365.camera.enums.DeviceOnlineStatus;
import com.pura365.camera.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 固件升级服务。
 * 根据设备当前固件版本自动选择同型号的最新固件包，并在下发前完成版本校验。
 */
@Service
public class FirmwareUpdateService {

    private static final Logger log = LoggerFactory.getLogger(FirmwareUpdateService.class);

    private static final Pattern CURRENT_VERSION_PATTERN =
            Pattern.compile("(?i)^v?(\\d+(?:\\.\\d+)+)");
    private static final Pattern PACKAGE_KEY_PATTERN =
            Pattern.compile("^((\\d+)(?:\\.\\d+)+)\\.([a-fA-F0-9]{32})$");

    @Value("${storage.qiniu.update-bucket:pura365-cloud-update}")
    private String qiniuUpdateBucket;

    @Value("${storage.vultr.update-bucket:pura365-cloud-update}")
    private String vultrUpdateBucket;

    @Value("${firmware.update.prefix:update/}")
    private String firmwarePrefix;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private StorageConfig.QiniuConfig qiniuConfig;

    @Autowired
    private StorageConfig.VultrConfig vultrConfig;

    @Autowired
    private RegionRoutingService regionRoutingService;

    public FirmwareUpgradeCommand prepareUpgradeCommand(String deviceId) {
        Device device = deviceRepository.selectById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在");
        }
        if (device.getStatus() != DeviceOnlineStatus.ONLINE) {
            throw new IllegalStateException("设备离线，无法执行固件升级");
        }

        String currentVersion = normalizeCurrentVersion(device.getFirmwareVersion());
        if (!StringUtils.hasText(currentVersion)) {
            throw new IllegalStateException("设备当前固件版本为空或格式不正确");
        }

        int currentModelCode = extractModelCode(currentVersion);
        FirmwarePackageInfo targetPackage = findLatestPackage(device, currentModelCode);
        if (targetPackage == null) {
            throw new IllegalStateException("未找到型号 " + currentModelCode + " 对应的升级固件");
        }

        int targetModelCode = extractModelCode(targetPackage.getVersion());
        if (currentModelCode != targetModelCode) {
            throw new IllegalStateException("固件型号不匹配，当前设备型号为 " + currentModelCode
                    + "，目标固件型号为 " + targetModelCode);
        }

        if (compareVersions(targetPackage.getVersion(), currentVersion) <= 0) {
            throw new IllegalStateException("当前已是最新固件版本");
        }

        log.info("已为设备 {} 选择固件包 - currentVersion={}, targetVersion={}, path={}",
                deviceId, currentVersion, targetPackage.getVersion(), targetPackage.getPath());

        return new FirmwareUpgradeCommand(
                device.getId(),
                currentVersion,
                targetPackage.getVersion(),
                targetPackage.getPath(),
                targetPackage.getMd5()
        );
    }

    private FirmwarePackageInfo findLatestPackage(Device device, int modelCode) {
        List<FirmwarePackageInfo> packages = regionRoutingService.isChina(device.getRegion())
                ? listQiniuPackages(modelCode)
                : listVultrPackages(modelCode);

        if (packages.isEmpty()) {
            return null;
        }

        return packages.stream()
                .max(Comparator.comparing(FirmwarePackageInfo::getVersion, this::compareVersions))
                .orElse(null);
    }

    private List<FirmwarePackageInfo> listQiniuPackages(int modelCode) {
        validateConfig(qiniuConfig.getAccessKey(), "七牛云 accessKey 未配置");
        validateConfig(qiniuConfig.getSecretKey(), "七牛云 secretKey 未配置");
        validateConfig(qiniuUpdateBucket, "七牛云固件更新桶未配置");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                qiniuConfig.getAccessKey(),
                qiniuConfig.getSecretKey()
        );

        try (S3Client s3Client = S3Client.builder()
                .region(Region.of("cn-south-1"))
                .endpointOverride(URI.create("https://s3.cn-south-1.qiniucs.com"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {
            return listPackagesFromBucket(s3Client, qiniuUpdateBucket, modelCode);
        } catch (Exception e) {
            log.error("读取七牛云固件更新桶失败 - bucket={}, modelCode={}", qiniuUpdateBucket, modelCode, e);
            throw new IllegalStateException("读取固件升级包失败，请稍后重试");
        }
    }

    private List<FirmwarePackageInfo> listVultrPackages(int modelCode) {
        validateConfig(vultrConfig.getEndpoint(), "Vultr endpoint 未配置");
        validateConfig(vultrConfig.getRegion(), "Vultr region 未配置");
        validateConfig(vultrConfig.getAccessKey(), "Vultr accessKey 未配置");
        validateConfig(vultrConfig.getSecretKey(), "Vultr secretKey 未配置");
        validateConfig(vultrUpdateBucket, "Vultr 固件更新桶未配置");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                vultrConfig.getAccessKey(),
                vultrConfig.getSecretKey()
        );

        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(vultrConfig.getRegion()))
                .endpointOverride(URI.create(vultrConfig.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()) {
            return listPackagesFromBucket(s3Client, vultrUpdateBucket, modelCode);
        } catch (Exception e) {
            log.error("读取 Vultr 固件更新桶失败 - bucket={}, modelCode={}", vultrUpdateBucket, modelCode, e);
            throw new IllegalStateException("读取固件升级包失败，请稍后重试");
        }
    }

    private List<FirmwarePackageInfo> listPackagesFromBucket(S3Client s3Client, String bucket, int modelCode) {
        List<FirmwarePackageInfo> result = new ArrayList<>();
        String prefix = normalizePrefix(firmwarePrefix) + modelCode + ".";
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(1000);
            if (StringUtils.hasText(continuationToken)) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            for (S3Object object : response.contents()) {
                FirmwarePackageInfo info = parsePackage(object.key());
                if (info != null) {
                    result.add(info);
                }
            }

            continuationToken = response.nextContinuationToken();
        } while (StringUtils.hasText(continuationToken));

        log.info("固件包扫描完成 - bucket={}, prefix={}, count={}", bucket, prefix, result.size());
        return result;
    }

    private FirmwarePackageInfo parsePackage(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }

        String normalizedPrefix = normalizePrefix(firmwarePrefix);
        String normalizedKey = objectKey.trim();
        if (!normalizedKey.startsWith(normalizedPrefix)) {
            return null;
        }

        String fileName = normalizedKey.substring(normalizedPrefix.length());
        Matcher matcher = PACKAGE_KEY_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }

        return new FirmwarePackageInfo(
                normalizedKey,
                matcher.group(1),
                matcher.group(3)
        );
    }

    private String normalizeCurrentVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }

        Matcher matcher = CURRENT_VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private int extractModelCode(String version) {
        String normalized = normalizeCurrentVersion(version);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalStateException("固件版本格式不正确: " + version);
        }
        String[] parts = normalized.split("\\.");
        return Integer.parseInt(parts[0]);
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int maxLength = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < maxLength; i++) {
            int leftValue = i < leftParts.length ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private String normalizePrefix(String prefix) {
        String normalized = StringUtils.hasText(prefix) ? prefix.trim() : "update/";
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private void validateConfig(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    public static class FirmwareUpgradeCommand {
        private final String deviceId;
        private final String currentVersion;
        private final String targetVersion;
        private final String path;
        private final String md5;

        public FirmwareUpgradeCommand(String deviceId, String currentVersion,
                                      String targetVersion, String path, String md5) {
            this.deviceId = deviceId;
            this.currentVersion = currentVersion;
            this.targetVersion = targetVersion;
            this.path = path;
            this.md5 = md5;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getTargetVersion() {
            return targetVersion;
        }

        public String getPath() {
            return path;
        }

        public String getMd5() {
            return md5;
        }
    }

    private static class FirmwarePackageInfo {
        private final String path;
        private final String version;
        private final String md5;

        private FirmwarePackageInfo(String path, String version, String md5) {
            this.path = path;
            this.version = version;
            this.md5 = md5;
        }

        public String getPath() {
            return path;
        }

        public String getVersion() {
            return version;
        }

        public String getMd5() {
            return md5;
        }
    }
}
