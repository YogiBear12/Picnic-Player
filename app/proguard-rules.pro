# Picnic Player ProGuard rules. R8 keeps most things via the AndroidX/Hilt/media3
# consumer rules; add app-specific keeps here as the codebase grows.

# Jellyfin SDK uses kotlin-logging which references SLF4J, but we don't provide an SLF4J binding
-dontwarn org.slf4j.**
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.** { *; }
