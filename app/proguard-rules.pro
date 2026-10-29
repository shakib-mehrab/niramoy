# ---- NIRAMOY release rules ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Tesseract JNI bridge
-keep class com.googlecode.tesseract.android.** { *; }
