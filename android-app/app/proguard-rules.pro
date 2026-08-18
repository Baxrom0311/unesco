# Keep WebView JS bridge methods, in case one is added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
