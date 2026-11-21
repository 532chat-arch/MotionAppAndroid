# ProGuard rules for Motion Studio
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.example.schema.**$$serializer { *; }
-keepclassmembers class com.example.schema.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.schema.** {
    kotlinx.serialization.KSerializer serializer(...);
}
