# The packing engine is plain Kotlin data classes and pure functions — nothing reflective,
# so no keep rules are needed for it. Add rules here as SDKs that do use reflection land
# (Room's generated code ships its own; billing will need checking).

# ONNX Runtime (YOLOX naming). Its native code finds these Java classes by name over JNI, so
# R8 must not rename or strip them, or the first inference aborts the app.
-keep class ai.onnxruntime.** { *; }
