-keepattributes *Annotation*, JavascriptInterface
-keepclassmembers class com.ansh.omnipage.ui.reader.ReflowBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.ansh.omnipage.ui.reader.OmniWebView { *; }
-keepclassmembers enum com.ansh.omnipage.** { *; }