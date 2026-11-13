# PDFBox carrega fontes e tabelas por nome: manter as classes inteiras.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
