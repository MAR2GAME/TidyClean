# Remote Config 参数

每个 `.json` 文件对应一个 Firebase Remote Config 参数：去掉文件名的 `.json` 后缀就是参数 key，文件的完整内容就是参数 value。参数使用 JSON 类型，直接粘贴文件内容，不需要额外包裹 key 或 `defaultValue`。

这些值来自当前项目 `core/src/main/res/raw/` 的默认配置，保留现有广告位、概率、频控、开关、广告 ID 和第三方 SDK 参数。各用户类型的策略初始值相同，可按运营需要分别调整。

| 参数 key | 用途 |
| --- | --- |
| `admob_config` | AdMob 广告 ID、缓存、超时及价格探测配置 |
| `common_config` | 服务端地址、接口路径、自动登录和 AB 测试配置 |
| `singular_config` | Singular 初始化及收入上报配置 |
| `thinking_config` | ThinkingData 初始化及日志上报时限 |
| `log_config` | 各渠道上报事件及批量上报配置 |
| `notification_content` | 通知文案、图片及场景配置 |
| `ad_config_UNKNOW` | 未知用户的广告策略（`UNKNOW` 为 SDK 实际拼写） |
| `ad_config_RISK` | 风险用户的广告策略 |
| `ad_config_NATURE` | 自然量用户的广告策略 |
| `ad_config_COMMON` | 普通买量用户的广告策略 |
| `ad_config_HIGH_VALUE` | 高价值用户的广告策略 |
| `notification_config_UNKNOW` | 未知用户的通知策略 |
| `notification_config_RISK` | 风险用户的通知策略 |
| `notification_config_NATURE` | 自然量用户的通知策略 |
| `notification_config_COMMON` | 普通买量用户的通知策略 |
| `notification_config_HIGH_VALUE` | 高价值用户的通知策略 |

参数名区分大小写。当前 SDK 读取的是带用户类型后缀的广告、通知参数，不读取 Remote Config 中单独的 `ad_config` 或 `notification_config`。

SDK 先读取原用户类型的 `ad_config_<用户类型>` 中的 `isRisk`。当该值为 `true` 且用户类型为 `UNKNOW` 或 `NATURE` 时，广告和通知均改用 `RISK` 策略；其他用户类型沿用各自策略。当前默认 `isRisk` 为 `false`，通知默认 `isSend` 为 `false`。

服务端已下发的广告、通知策略优先于对应的 Remote Config 策略。远端参数缺失、为空或不是有效 JSON 对象时，SDK 沿用当前配置；启动时先加载 raw 默认值，再加载本地已缓存的配置。

代码依据：`core/src/main/java/com/mar2sdk/core/common/RiskUtil.kt`、`core/src/main/java/com/mar2sdk/core/common/status/UserType.kt`。
