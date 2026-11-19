-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
# Filament (3D car): its native code calls back into these classes by name.
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
