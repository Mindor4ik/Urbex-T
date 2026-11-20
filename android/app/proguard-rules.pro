-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Serialized Domain & Workspace models (including StoredWorkspaceIndex in :data)
-keep class com.urbext.domain.model.** { *; }
-keepclassmembers class com.urbext.domain.model.** { *; }
-keep class com.urbext.data.repository.StoredWorkspaceIndex { *; }
-keep class com.urbext.data.repository.StoredWorkspaceIndex$** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Room Database, Entities, DAOs, and Migrations
-keep class * extends androidx.room.RoomDatabase
-keep class com.urbext.data.local.** { *; }

# WorkManager reflective workers (BackupVerifyWorker)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.urbext.data.worker.** { *; }
-keep class com.urbext.data.sync.** { *; }

# MapLibre GL & OkHttp
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.mapbox.**
-dontwarn okhttp3.**
-dontwarn okio.**
