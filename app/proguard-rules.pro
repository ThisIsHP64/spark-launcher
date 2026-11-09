# keep line numbers so user-submitted stack traces stay readable without any crash reporting sdk
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# release builds must not write to logcat, since messages can name other installed apps
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
