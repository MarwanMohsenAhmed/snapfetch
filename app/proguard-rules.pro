# R8 full mode optimization rules for SnapFetch
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'o'

# Strip debug logs in release for speed & APK budget
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.snapfetch.app.**$$serializer { *; }
-keepclassmembers class com.snapfetch.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.snapfetch.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# NewPipeExtractor + Rhino + Protobuf + Jsoup
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class com.grack.nanojson.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.**
-dontwarn org.jsoup.**
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Media3 / ExoPlayer / Transformer
-dontwarn androidx.media3.**
-keep class androidx.media3.decoder.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
