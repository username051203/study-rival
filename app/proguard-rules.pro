-keep class com.studyrival.omega.MainActivity$AndroidBridge { *; }
-keepclassmembers class com.studyrival.omega.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
