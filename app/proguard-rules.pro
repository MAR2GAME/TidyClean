# Preserve generic type information used by the ViewBinding and ViewModel base classes.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-allowaccessmodification

# Android framework and XML entry points are also covered by AAPT-generated rules.
-keep public class * extends android.app.Activity {
    public <init>();
}
-keep public class * extends android.app.Application {
    public <init>();
}
-keep public class * extends android.app.Service {
    public <init>();
}
-keep public class * extends android.content.BroadcastReceiver {
    public <init>();
}
-keep public class * extends android.content.ContentProvider {
    public <init>();
}

-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Binding classes are resolved from generic types. Their inflate method names are
# string-based reflection entry points and must not be obfuscated.
-keep,allowoptimization,allowobfuscation class com.tidyclean.storagecleaner.databinding.**
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.databinding.** {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}
# ViewModels are resolved from generic types and instantiated by ViewModelProvider.
-keep,allowoptimization,allowobfuscation class com.tidyclean.storagecleaner.viewmodel.** extends androidx.lifecycle.ViewModel {
    public <init>();
}

# Standard runtime contracts.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# EventBus discovers subscriber methods by annotation at runtime.
-keepclassmembers,allowoptimization class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# Gson reads application model fields reflectively.
-keep,allowoptimization,allowobfuscation class com.tidyclean.storagecleaner.bean.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Honor explicit keep annotations without retaining unrelated AndroidX code.
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <init>(...);
}

# Release builds do not need application logging calls.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Optional platform/vendor APIs are accessed defensively or may be absent.
-dontwarn android.webkit.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.api.client.**
-dontwarn com.google.ads.**
-dontwarn org.w3c.dom.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# Firebase Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }

# Firebase Crashlytics
-keep class com.crashlytics.** { *; }
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.crashlytics.**
-dontwarn com.google.firebase.crashlytics.**

# Firebase Analytics
-keep class com.google.firebase.analytics.FirebaseAnalytics { *; }
-keep class com.google.android.gms.measurement.AppMeasurement { *; }
-keep class com.google.android.gms.measurement.AppMeasurement$ConditionalUserProperty { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**


# ========== Google Firebase 消息服务 ==========
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }

# ========== Google Play Services ==========
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
# 混淆防重打包类
-keep class com.tidyclean.storagecleaner.utils.SafeChecker {*;}
-keep class com.airbnb.lottie.** { *; }





# 保留 DataModel 相关的类及其公共成员（包括 getter/setter），以确保序列化正常工作
-keep class com.thinkingdata.analytics.** { *; }
-keep class cn.thinkingdata.android.** { *; }




# Unity Ads
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.ads.**

# IronSource
-keep class com.ironsource.** { *; }
-dontwarn com.ironsource.**

# Vungle
-keep class com.vungle.** { *; }
-dontwarn com.vungle.**

# Chartboost
-keep class com.chartboost.** { *; }
-dontwarn com.chartboost.**

# Fyber
-keep class com.fyber.** { *; }
-dontwarn com.fyber.**

# Mintegral
-keep class com.mbridge.** { *; }
-dontwarn com.mbridge.**

# ByteDance (Pangle)
-keep class com.bytedance.** { *; }
-dontwarn com.bytedance.**

# InMobi
-keep class com.inmobi.** { *; }
-dontwarn com.inmobi.**

# Mintegral
-keep class com.mintegral.** { *; }
-dontwarn com.mintegral.**


# ========== Singular SDK ==========
-keep class com.singular.sdk.** { *; }
-keep class com.singular.** { *; }
-keepclassmembers class com.singular.sdk.* { *; }

# ========== AppLovin ==========
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**
-keep class com.applovin.sdk.** { *; }

# ==========  mediation 适配器 ==========
-keep class com.applovin.mediation.** { *; }
-keep class com.google.ads.mediation.** { *; }


-keep class com.google.android.ump.** { *; }
