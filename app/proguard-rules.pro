# ROC toolkit JNI wrapper keep rules.
#
# roc-android:0.2.1 ships no consumer ProGuard/R8 rules. Its native library
# (libroc_jni.so) resolves Java members by name via JNI
# (Java_org_rocstreaming_roctoolkit_<Class>_<method>) and reads fields/enum
# values of the config classes. R8 cannot see these JNI/name lookups, so the
# wrapper package must be preserved in full.
-keep class org.rocstreaming.roctoolkit.** { *; }

# Lombok's @Generated is a CLASS-retention annotation baked into the ROC
# wrapper bytecode. The annotation class is not shipped and is not needed at
# runtime, so R8's missing-class warning for it is a false positive.
-dontwarn lombok.Generated
