# R8 配置分析

更新日期：2026-10-22。下方基线数据来自本地 1.0.4 / 1.0.5 AAB。用户已确认所有广告 SDK 都在使用，因此本次保留全部广告源，仅升级 Vungle adapter 至 7.7.4.1，使用官方已收窄规则的 SDK 7.7.4。没有修改 keep 文件。

## 截图结论

截图显示 Production 为 Active，最近版本为 5（1.0.5），提示 `DEX code optimization is below our threshold`，其中 `Obfuscation (24%)` 低于页面要求的 25%，整改期限为 2027 年 2 月。该页面说明存在优化告警，不能单凭此截图认定这就是审核拒绝原因；如另有拒审通知，需对照 Policy status / 拒审邮件中的具体条款。

## 当前配置与产物证据

- AGP 8.12.0、Gradle 8.13。`app/build.gradle.kts:102` 已开启混淆、资源收缩，并使用 `proguard-android-optimize.txt`。
- 未关闭 R8 full mode；`gradle.properties` 已设置 `android.r8.optimizedResourceShrinking=true`。
- `core/build.gradle.kts:27` 导出 `consumer-rules.keep`，保留自定义 AdMob 内部反射入口。其他本地库规则文件为空或只有模板注释。
- 应用侧 SDK 整包保留规则已在此前提交中移除，ViewModel 包名已修正为实际的 `viewmodle`，Gson 保留范围已缩小。继续建议做同一次修改不能解决剩余问题。
- 优化前的 release `mapping.txt` 与本地 `app/release/com.tidyclean.storagecleaner-1.0.5.aab` 内嵌 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` 大小均为 119,745,021 字节，SHA-256 均为 `d2b60490a6f13d64f6629026b0b778e02961afc2c323a2d625e940ce85562174`。因此基线统计对应本地 1.0.5 AAB；当前构建输出已更新为 1.0.6，验证数据见后文。

按 mapping 中原始类名与输出类名是否不同统计：

| 范围 | 总类数 | 类名改写数 | 比例 |
| --- | ---: | ---: | ---: |
| 1.0.4 全部 | 40,106 | 7,473 | 18.63% |
| 1.0.5 全部 | 38,056 | 9,244 | 24.29% |
| 1.0.5 应用包 com.tidyclean.storagecleaner | 226 | 201 | 88.94% |
| com.google.android | 11,814 | 1,201 | 10.17% |
| com.mbridge.msdk | 3,490 | 23 | 0.66% |
| com.bytedance.sdk | 2,948 | 4 | 0.14% |
| com.fyber.inneractive | 1,650 | 3 | 0.18% |
| com.inmobi.media | 1,522 | 72 | 4.73% |
| com.ironsource.adqualitysdk | 1,094 | 0 | 0% |
| com.unity3d.services | 1,071 | 5 | 0.47% |
| com.unity3d.ads | 1,064 | 2 | 0.19% |

此统计不是 Google Play 的检测算法。供应商发布前已经混淆的类也可能被计为未改名，不能用本地比例承诺通过审核。

## 1.0.5 基线：依赖自带的整包保留规则

以下行号取自 1.0.5 构建时的 `app/build/outputs/mapping/release/configuration.txt`，来源注释可追溯至各依赖 consumer rules；当前 1.0.6 输出的行号可能不同。

| 来源及依赖声明位置 | 规则证据 | 建议 |
| --- | --- | --- |
| InMobi adapter 11.1.0.0，core/build.gradle.kts:68 | 第 443 行：`-keep public class com.google.android.gms.**` | 已核对新版仍有该规则。本次保留依赖和规则，最终本地指标改善后无需额外过滤。 |
| Vungle SDK 7.6.1，由 core/build.gradle.kts:70 的 adapter 引入 | 第 1422 行：`-keep class com.google.android.gms.** { *; }` | 已升级至 adapter 7.7.4.1 / SDK 7.7.4，由官方规则移除整个 GMS 包及成员的保留。 |
| Mintegral，core/build.gradle.kts:72 | 第 2685、2686 行保留 com.mbridge.** 类、接口和全部成员 | 本次保留，后续优化需核对供应商规则。内部精确规则被整包规则覆盖，单删精确规则无效。 |
| Pangle，core/build.gradle.kts:73 | 第 1290、1291 行保留 com.bytedance.sdk.** | 本次保留，后续优化需核对供应商规则。 |
| Fyber / DT Exchange，core/build.gradle.kts:67 | 第 2619 行保留 com.fyber.inneractive.sdk.** 全部成员 | 同上。 |
| ironSource，core/build.gradle.kts:69 | 第 2291 行保留 com.ironsource.** 全部成员，另有相关 Unity 包规则 | 同上；只删被整包规则覆盖的子包规则没有作用。 |
| Unity，core/build.gradle.kts:74、75 | 第 353、358 行保留 com.unity3d.ads.**、com.unity3d.services.** | 本次保留 adapter 及 SDK，后续优化需核对供应商规则。 |

`com.google.android.gms.internal.ads` 有 6,620 个类，仅 4 个类名改写。InMobi 与 Vungle 的跨包规则同时存在，只处理其中一个不能解除另一个的约束。规则按约束合并，追加 `allowobfuscation` 或调整规则顺序不能覆盖已有强保留。

不要直接编辑 Gradle 缓存中的 proguard.txt，也不要未经反射、JNI、广告加载验证就屏蔽整个 SDK 的 consumer rules。本次已核对 Vungle SDK 7.7.4 官方 AAR 的规则修复，并验证最终构建产物，详见后文。

## 应用侧规则逐项建议

以下以 `app/proguard-rules.pro` 为准。常规库规则优先依赖库自带配置；自定义反射入口需要按实际代码保留。

| 规则 | 建议与依据 |
| --- | --- |
| 第 8、11 行，所有 View 的两种构造器 | XML 引用由 AAPT 生成规则。确认没有额外动态构造入口后可移除，或仅保留实际动态创建的 View 构造器。 |
| 第 28 行 native 方法、第 31 行 enum、第 35 行 Parcelable | 默认优化文件已有对应规则，可删除应用侧重复项。 |
| 第 48 行 @Subscribe | EventBus 3.3.1 自带对应成员规则，可删除重复声明。业务确有 MainActivity、CleanJunkOrFilesActivity、LargeFileManagerActivity 订阅方法，继续依赖库规则。 |
| 第 69 行 @SerializedName 字段 | Gson 2.14.0 自带规则，可删除重复声明。当前全局声明还禁止字段改名，比库规则更严格。 |
| 第 74、75 行 AndroidX @Keep | 依赖已提供对应规则，可删除应用侧重复声明，不删除库自身规则。 |
| 第 18 行 ViewBinding inflate | BaseViewActivity:22、BaseMvvmActivity:42、BaseMvvmFragment:49 按字符串反射方法。保留这两个方法签名及必要类型信息；若进一步收窄，应按这三个基类调用路径列出具体绑定类。 |
| 第 23 行 ViewModel 构造器 | 实际包名 viewmodle 已修正。基类通过泛型和 ViewModelProvider 构造类型，保留实际构造器和泛型链即可，当前允许类名混淆。 |
| 第 38 行 Serializable 特殊成员 | 属于序列化契约，影响有限。若收窄，先核实 Java 序列化或 Intent / Bundle 传递的具体类型，再限制范围。 |
| 第 54、57、60、66 行 Play Integrity 模型字段 | SafeChecker:231 使用 Gson，模型尚无 @SerializedName。保留字段名，或先为全部实际 JSON 字段加准确注解，再依赖 Gson 自带规则。 |
| 第 63 行 DeviceIntegrity 字段 | 当前类为空，无字段匹配，可以删除这条规则。 |
| 第 2 行属性 | 泛型与注解仍用于反射，保留 Signature、InnerClasses、EnclosingMethod 和相关运行时注解。源码行号用于堆栈定位，不是整包不混淆的根因。 |
| 第 3 行 allowaccessmodification | 默认优化文件已有，可去重；不属于保留约束。 |
| 第 82 行 Log 副作用声明 | 与本次类名保留问题无直接关系，无需为比例修改。 |
| 第 91–99 行 dontwarn | 只屏蔽诊断，不会开启混淆。后续按实际缺失类收窄，增加它们不能解决比例问题。 |

这些去重无法抵消 SDK 的整包规则，不能保证跨过门槛。

## 自定义 AdMob 反射规则

`core/consumer-rules.keep` 对应 AdmobReflectProbe.kt 明确的类名、字段名、字段类型和继承链检查，不是 Google SDK 的普通重复保留：

- zzbgm.zzb、zzcvn.zza、zzbss.zzc、zzets.zzj、zzccy.zzb、zzfke.zzi：保留精确字段及描述符类型。
- zzbgo、zzbel.zza：保留 Binder 代理路径。
- zzcvm、zzdmh、zzdvu：保留反射依赖的中间类型及层级。
- zzcya.zzb、zzfkn.zzae、com.google.android.gms.ads.internal.client.zzt 的四个指定字段：保留价格读取链。

以上精确规则应继续保留。它们在 1.0.5 中被第三方 GMS 整包规则覆盖，解除整包保留后仍然必要。升级 AdMob 必须重新验证内部类及字段路径。

## 保留全部广告源的实际修改

用户确认九个广告 SDK 都使用，不采用删除依赖方案。`core/build.gradle.kts` 将 Vungle adapter 从 7.6.1.0 升级到 7.7.4.1，其余广告源保留。

- 已下载检查官方 SDK 7.7.4 AAR：不再包含 Google 全包 keep，保留 Vungle 与 OMID 自身包的规则。
- Adapter 7.7.4.1 POM 依赖 Vungle SDK 7.7.4 和 Google Mobile Ads 25.2.0，允许项目继续使用显式声明的 25.3.0。
- 新 adapter 最低 Android 为 API 23、SDK 为 API 21，均低于项目 minSdk 29。
- 未选最新 Vungle adapter 7.7.9.0：它会引入 AdMob 25.5.0，当前价格反射代码只支持 25.3.0。
- InMobi 11.1.0.0、11.3.0.0、11.5.0.1 的 adapter 规则经检查仍相同，升级不能移除其 public Google 类保留。本次保留此规则，最终产物的本地统计为 28.25%，仍需由 Play 重新检测。

官方证据：

- [Adapter 7.7.4.1 POM](https://dl.google.com/dl/android/maven2/com/google/ads/mediation/vungle/7.7.4.1/vungle-7.7.4.1.pom)
- [Vungle SDK 7.7.4 AAR](https://repo.maven.apache.org/maven2/com/vungle/vungle-ads/7.7.4/vungle-ads-7.7.4.aar)

版本已更新为 versionCode 6、versionName 1.0.6。最终应使用 release 包回归启动、ViewBinding / ViewModel 页面、EventBus、Play Integrity、各广告源实际加载及展示，再上传 AAB 由 Play 重新计算指标。debug 测试不能替代 release 回归。

## 本次构建与产物验证

- `:app:bundleRelease :app:assembleRelease` 首次成功，耗时 8 分 19 秒；递增版本号后的最终构建再次成功，耗时 6 分 10 秒。本地验证未上传 Crashlytics mapping。
- 最终 AAB 内嵌 mapping 与本地 mapping 的 SHA-256 相同：`e526108fccf2d9261a3e448e11eaa99e65038e59342e811866f43db4481ad315`。合并规则确认使用 Vungle SDK 7.7.4。仍有一条 InMobi 的 public Google 类保留规则，本次不需要进一步修改它。
- 同一统计方法下，类名改写从 9,244 / 38,056（24.29%）提高到 10,139 / 35,888（28.25%）。Google Play 实际检测结果仍需上传后确认。
- mapping 中九家广告适配器入口与十七个 AdMob 反射相关类保留。未改名字段可能不出现在 mapping 中，所以字段验证不以缺失 mapping 条目判失败。
- 在 API 36 模拟器上使用 Android PathClassLoader 加载最终 1.0.6 release APK 的临时副本，15 项检查通过：MobileAds 实测为 25.3.0；开屏、插屏、激励的价格反射链、四个价格事件字段、本地 Binder 包装路径和九家适配器公共构造入口均通过。临时副本 SHA-256 与本地最终 APK 相同。
- 最终 1.0.6 在 API 36 模拟器上安装与启动成功，扫描页、首页正常显示，首页截图已确认包含清理入口、App Usage、Speaker cleaner 及底部导航。本轮未扩大到全部工具页。
- 上述反射验证不包含远程 Play services Dynamite 模块本体，也不代表九家广告源的真实请求、填充及展示均已验证。

最终 AAB 为 `app/release/com.tidyclean.storagecleaner-1.0.6.aab`，版本为 6（1.0.6），大小 37,834,055 字节，SHA-256 为 `55b9aea2e7971d39a433e1bbbec32313c98351dc3e770e38709be3b4f4d80c5a`。未上传或发布到 Play。

后续可独立评估升级 AGP 9，但不能假定升级会消除整包 keep。当前 AGP 8.12 已开启相关优化配置，建议先完成最小依赖修复，再安排工具链迁移。

历史记录：此前分析记载 release assemble 成功，以及 debug core:AdConfigTest 的两个 fixture 失败；本次未复跑，不将历史记录作为当前修改的验证结果。
