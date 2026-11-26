# Preserve stack trace line numbers for production diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization (used by Supabase DTOs, Ktor, and navigation/state)
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Supabase & Ktor Android engine
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
-dontwarn kotlinx.atomicfu.**
-dontwarn java.lang.instrument.**
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# CameraX & ML Kit Barcode Scanning
-keep class androidx.camera.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.vision.barcode.** { *; }

# ZXing QR Code Generator
-keep class com.google.zxing.** { *; }

# App domain & data DTOs
-keep class com.example.data.sync.** { *; }
-keep class com.example.data.auth.** { *; }
-keep class com.example.model.** { *; }
