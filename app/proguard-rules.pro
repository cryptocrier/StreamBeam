# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Retrofit and Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }

# Keep model classes
-keep class com.streambeam.model.** { *; }
-keep class com.streambeam.addons.** { *; }

# Keep Cast
-keep class com.google.android.gms.cast.** { *; }

# Keep ExoPlayer
-keep class androidx.media3.** { *; }