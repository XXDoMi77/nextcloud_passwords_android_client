# R8 configuration.
#
# The app is open source, so obfuscation buys nothing; shrinking and optimisation do. The keeps below
# are therefore as narrow as they can be, rather than the blanket "keep the whole package" this file
# used to carry - that rule kept every class and member alive and left R8 with nothing to remove.

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# --- Gson -----------------------------------------------------------------------------------------
# Generic types are read at runtime: the stored document is a `Data` holding `List<Password>` and
# friends, and TypeToken resolves those from the Signature attribute. R8 strips attributes it is not
# told to keep, and without Signature the element type erases - Gson then builds LinkedTreeMap instead
# of the data class, which is what made an R8 build fail on startup rather than at build time.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Gson instantiates these reflectively and matches fields by @SerializedName, so the fields have to
# survive with their annotations. Nothing constructs them by name outside deserialisation, so keeping
# the members of this one package is enough.
-keep class com.dominikdomotor.nextcloudpasswords.dataclasses.** { *; }

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# --- JNA and libsodium ----------------------------------------------------------------------------
# lazysodium binds native symbols through JNA, which maps Java interfaces onto the library by
# reflection at runtime. Renaming or removing any of it breaks the binding in a way that only shows up
# when end-to-end encryption is actually exercised.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.goterl.lazysodium.** { *; }
-dontwarn java.awt.**

# --- Kotlin ---------------------------------------------------------------------------------------
# Coroutine internals are looked up reflectively for debug metadata.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Crash reports are worth reading, and line numbers cost almost nothing.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
