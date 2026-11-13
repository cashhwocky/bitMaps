# MapLibre, OkHttp and Compose keep their own rules; nothing reflective in bitMaps.
-dontwarn org.maplibre.**
-keep class org.maplibre.android.** { *; }

# sherpa-onnx (voices) is called from native code; keep its classes intact.
-keep class com.k2fsa.sherpa.onnx.** { *; }
