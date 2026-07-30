# KioskRelay does not expose JavaScript interfaces. Keep protobuf-lite messages
# available to DataStore while allowing R8 to optimize the remainder of the app.
-keep class io.github.kioskrelay.proto.** { *; }

# Preserve useful source names and line numbers in local crash diagnostics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
