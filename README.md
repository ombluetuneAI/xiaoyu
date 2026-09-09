# 小鱼助手 Android APK (`xiaoyu-app`)

依据 `docs/XIAOYU_APK_DESIGN.md` v3.1 · UI 稿 v1.3 · APK 一体化 MCP 架构。

## 模块

| 模块 | 职责 |
|------|------|
| `app` | Activity、引导、设置、播放页 |
| `service` | `XiaoyuAssistantService` FGS、依赖注入图 |
| `core-voice` | OTA、绑定、语音 WS、设备身份 |
| `core-session` | `VoiceSessionManager` 状态机 |
| `core-wake` | KWS（**当前为能量阈值占位，非 Sherpa-ONNX**） |
| `core-media` | MediaResolver、TXB API、ExoPlayer、队列 |
| `core-mcp` | `DeviceMcpServer` |
| `core-router` | CommandRouter、MediaCommandHandler |
| `core-registry` | AppAdapterRegistry |
| `core-link` | XiaoyuLink 消息 |

## 唤醒（KWS 占位说明）

`SherpaWakeEngine` 当前使用 **AudioRecord + RMS 能量阈值 + 连续帧防抖** 作为可演示唤醒路径，**不是**真实「小鱼同学」Sherpa-ONNX 模型。日志 tag：`SherpaWakeEngine`。

可靠唤醒方式（QA / 调试）：

1. 通知栏 **「点击唤醒」** 按钮（等同唤醒词触发语音会话）
2. 设置页 **「启动语音服务（调试）」** 确保 FGS 常驻
3. 大声说话触发能量阈值（环境噪声高时可能误触或难触发）

量产须替换 `detectWakeWord` 为 Sherpa-ONNX 推理，模型放 `assets/sherpa/`。

## 构建

```bash
cd xiaoyu-app
./gradlew :app:assembleDebug
./gradlew :core-voice:testDebugUnitTest
```

需安装 Android SDK 与 JDK 17。

## 真机验收

1. 完成引导 → 绑定小智（A-10/A-11）
2. 启动后台服务 → 通知栏「小鱼同学待命中」（S-01）；可点「点击唤醒」
3. 配置 TXB 地址 → 设置页「测试播放（调试）」或语音点歌（A-15～A-17）
4. 点击 MediaSession / 设置「正在播放」→ PlayerActivity（A-12）

## 待集成

- Sherpa-ONNX 真实唤醒词模型
- 完整小智 WS Opus 编解码（当前 PCM 占位上行）
- XiaoyuLink 定向广播 + 5s 回执超时（A-21 量产）
