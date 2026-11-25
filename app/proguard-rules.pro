# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.karelisio.patoune.** {
    *** Companion;
}
-keepclasseswithmembers class com.karelisio.patoune.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.karelisio.patoune.**$$serializer { *; }
# Room / WorkManager workers instantiated by reflection
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn com.google.errorprone.annotations.**
