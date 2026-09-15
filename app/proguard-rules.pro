# 保留 Javascript 交互接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 AndroidX 控件
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }
