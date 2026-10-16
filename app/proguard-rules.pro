# Preserve generic type information used by the ViewBinding and ViewModel base classes.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-allowaccessmodification

# XML custom view constructors may be invoked by LayoutInflater. Android manifest
# components are covered by AGP's generated rules, so do not keep every framework
# component in every dependency.
-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers,allowoptimization,allowobfuscation class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Binding classes are resolved from generic types. Their inflate method names are
# string-based reflection entry points and must not be obfuscated. The classes
# themselves may still be obfuscated because the code obtains them from Signature.
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.databinding.** {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}
# ViewModels are resolved from generic types and instantiated by ViewModelProvider.
-keep,allowoptimization,allowobfuscation class com.tidyclean.storagecleaner.viewmodle.** extends androidx.lifecycle.ViewModel {
    <init>(...);
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

# Gson reads these application model fields reflectively. Keep the JSON field
# names, while allowing the model class names and unused members to be obfuscated.
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.bean.PlayIntegrityBean {
    <fields>;
}
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.bean.AccountDetails {
    <fields>;
}
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.bean.AppIntegrity {
    <fields>;
}
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.bean.DeviceIntegrity {
    <fields>;
}
-keepclassmembers,allowoptimization class com.tidyclean.storagecleaner.bean.RequestDetails {
    <fields>;
}
-keepclassmembers,allowoptimization class * {
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

# Firebase, Google Mobile Ads, mediation adapters, Lottie, ThinkingData,
# Singular and AppLovin publish consumer rules in their AARs. Do not duplicate
# package-wide -keep rules here, because they prevent R8 from obfuscating the
# SDK code that the libraries already describe accurately.
