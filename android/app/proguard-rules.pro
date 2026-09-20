# Keep R8 rules for Phase 2+ (serialization, Coil, Media3, Room).
# TODO(P2): add -keep rules when models land.

# P4: atomashpolskiy/bt uses Guice runtime DI — keep its surface (device behavior
# UNVERIFIED; debug builds don't minify, release needs on-device confirmation).
-keep class bt.** { *; }
-keep class com.google.inject.** { *; }
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
# bt transitives reference desktop-only APIs on dead paths (snakeyaml beans,
# httpclient javax.naming fallback, slf4j NOP binding) — never loaded on device.
-dontwarn java.beans.**
-dontwarn javax.naming.**
-dontwarn org.slf4j.impl.**
-dontwarn java.lang.reflect.AnnotatedType
# Verified by bytecode diff: of 56 com.google.common.* refs in guice-5.0.1,
# Streams is the ONLY one missing from the android-flavor guava, and it is
# referenced solely from ChildBindingAlreadySetError's ctor (duplicate module
# bindings — a wiring bug our JVM tests would catch first). Never loaded
# on healthy paths, so dontwarn is safe here.
-dontwarn com.google.common.collect.Streams
