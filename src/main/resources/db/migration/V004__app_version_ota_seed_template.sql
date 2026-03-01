-- App OTA 版本配置模板（camera_app 库）
-- 说明：
-- 1) app_version 表已存在，无需新增表
-- 2) 每次发版插入一条新记录，服务端按 id 倒序取最新一条

USE camera_app;

-- Android 版本（download_url 填 APK 直链）
INSERT INTO app_version (
  platform,
  latest_version,
  min_version,
  download_url,
  release_notes,
  force_update,
  created_at,
  updated_at
) VALUES (
  'android',
  '1.0.1',
  '1.0.0',
  'https://your-cdn.example.com/pura365_1.0.1.apk',
  '1. 修复已知问题\n2. 优化体验',
  0,
  NOW(),
  NOW()
);

-- iOS 版本（download_url 填 App Store 链接）
INSERT INTO app_version (
  platform,
  latest_version,
  min_version,
  download_url,
  release_notes,
  force_update,
  created_at,
  updated_at
) VALUES (
  'ios',
  '1.0.1',
  '1.0.0',
  'https://apps.apple.com/app/id0000000000',
  '1. 修复已知问题\n2. 优化体验',
  0,
  NOW(),
  NOW()
);

-- 验证当前生效版本（后端取每个平台最新 id）
SELECT *
FROM app_version
WHERE platform = 'android'
ORDER BY id DESC
LIMIT 1;

SELECT *
FROM app_version
WHERE platform = 'ios'
ORDER BY id DESC
LIMIT 1;
