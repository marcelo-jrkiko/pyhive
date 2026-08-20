# Retrofit
-keep interface krs.pyhive.api.** { *; }

# Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Chaquopy
-keep class com.chaquo.python.** { *; }

# Timber
-dontwarn org.jetbrains.annotations.**
