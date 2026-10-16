# R8 配置分析

## 当前配置

- Android Gradle Plugin 为 8.12.0，R8 由 AGP 自动执行。
- `app` 的 `release` 构建已启用 `isMinifyEnabled = true` 和 `isShrinkResources = true`。
- `release` 使用 AGP 的 `proguard-android-optimize.txt`，并叠加 `app/proguard-rules.pro`。
- `gradle.properties` 已开启 `android.r8.optimizedResourceShrinking=true`，使 AGP 8.12 使用优化的资源收缩流程。
- `core` 通过 `consumer-rules.keep` 向应用传递 AdMob 内部反射所需的精确字段和类型规则；`impl`、`debug` 的模板规则目前为空。

## 规则检查

### 需要保留

- `Signature`、`InnerClasses`、`EnclosingMethod` 以及注解属性：应用通过泛型获取 ViewBinding 和 ViewModel 类型。
- ViewBinding 类及其 `inflate` 方法：`BaseViewActivity`、`BaseMvvmActivity` 和 `BaseMvvmFragment` 使用反射调用这些方法。
- EventBus 的 `@Subscribe` 方法成员规则：订阅方法由 EventBus 在运行时发现。
- `core/consumer-rules.keep` 中针对 `com.google.android.gms.internal.ads` 的字段、描述符和层级规则：`AdmobReflectProbe` 使用 `Class.forName` 和字段名读取这些类型。

### 需要收窄

- ViewModel 规则当前写的是 `com.tidyclean.storagecleaner.viewmodel.**`，实际源码包名是 `com.tidyclean.storagecleaner.viewmodle`，基类位于 `com.tidyclean.storagecleaner.base.viewmodel`。应改为实际包名下的 ViewModel 类和构造器，或改用具体类列表。
- `com.tidyclean.storagecleaner.bean.** { *; }` 会保留整个 bean 包。Gson 当前明确反射读取的是 `PlayIntegrityBean` 及其嵌套模型，应改为这些模型的字段规则，优先使用 `@SerializedName` 字段匹配。
- `com.google.firebase.**`、`com.google.android.gms.**`、各广告 mediation SDK、Lottie、ThinkingData、Singular 和 AppLovin 的整包 `-keep` 规则范围过大。多数依赖已经提供 consumer rules，应删除重复的整包保留规则，再根据 release 构建的 missing-class 诊断补充精确规则。
- `-dontwarn com.google.android.gms.**` 等整包 `-dontwarn` 应按实际缺失类逐项处理，避免隐藏真正的配置问题。

### 可移除或依赖库规则

- AndroidX、Kotlin、Gson、EventBus、Glide、Firebase、Google Mobile Ads 以及 mediation 依赖的常规运行时规则优先使用各自的 consumer rules。
- Activity、Application、Service、Receiver 和 Provider 的 manifest 入口由 Android 构建工具生成的规则覆盖；只有新增动态入口时才补充具体类。

## AGP 迁移建议

当前 AGP 8.12 已支持本项目的 R8 配置。后续升级到 AGP 9 时应同步检查 Gradle、JDK 和 Kotlin 插件版本，并重新验证上述反射入口。

## 验证

使用 `./gradlew.bat :app:assembleRelease` 验证 release 构建，R8、资源收缩、打包和签名流程均完成。

构建日志仍提示 `cn.thinkingdata.ta_apt.TRoute` 和 `com.facebook.infer.annotation.Nullsafe` 缺失；当前构建未失败，后续应结合实际 SDK 使用情况决定是否补充依赖或针对性处理警告。

收窄 ViewModel、Gson 和 SDK 规则后，应使用 UI Automator 覆盖启动页、ViewBinding 页面、ViewModel 页面、EventBus 页面和广告初始化流程，确认反射入口在压缩包中仍可用。

本地 debug 单元测试中 `core:AdConfigTest` 有 2 项既有配置 fixture 失败：测试优先读取了 `app/src/main/res/raw/ad_config.json`，其中没有测试用的 `testNative*` 数据；这与 R8 规则无关。

## 本次收窄

- 移除了应用侧对 Firebase、Google Mobile Ads、各 mediation SDK、Lottie、ThinkingData、Singular 和 AppLovin 的整包 `-keep`；这些依赖继续使用 AAR 自带的 consumer rules。
- 修正了 ViewModel 的实际包名 `viewmodle`，并只保留 ViewModel 构造器。
- 将 Gson 保留范围从整个 `bean` 包缩小到 Play Integrity 响应模型的字段。
- 重新构建后，mapping 中被改写的类比例从约 18.6% 提升到约 24.3%。Google Play 的最终指标需要在上传新的 AAB 后重新计算；若仍低于 25%，应优先移除未使用的 mediation SDK 依赖，而不是强制覆盖 SDK 自带的反射规则。
