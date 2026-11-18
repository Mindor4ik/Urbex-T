-keepattributes *Annotation*, InnerClasses
-keep class com.urbext.domain.model.** { *; }
-keepclassmembers class com.urbext.domain.model.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn org.maplibre.**
-dontwarn okhttp3.**
-dontwarn okio.**

