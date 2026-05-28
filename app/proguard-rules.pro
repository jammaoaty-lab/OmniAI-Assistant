-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.omniai.assistant.nativebridge.** { *; }
-keep class com.omniai.assistant.model.** { *; }
-keep class com.omniai.assistant.common.Result { *; }

-keepclassmembers class com.omniai.assistant.nativebridge.LlamaBridge {
    native <methods>;
}

-keepclassmembers class * {
    public <init>(...);
}

-dontwarn io.noties.markwon.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.gson.**

-keep class com.google.gson.** { *; }
-keep class io.noties.markwon.** { *; }
