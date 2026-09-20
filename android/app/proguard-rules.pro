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
