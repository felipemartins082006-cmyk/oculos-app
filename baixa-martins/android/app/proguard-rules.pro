# Métodos chamados pelo JavaScript da página
-keepclassmembers class com.martins.baixa.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Biblioteca do yt-dlp (usa reflexão / Jackson)
-keep class com.yausername.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.**
