# Room: generated implementations + entities keep their schema-mapped fields.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Hilt / Dagger generated components.
-keep class dagger.hilt.internal.aggregatedroot.codegen.* { *; }
-keep class hilt_aggregated_deps.* { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# kotlinx.serialization: keep generated *$$serializer companions and the
# @Serializable model classes they reflect into.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sualtikasifi.cizimhafiza.**$$serializer { *; }
-keepclassmembers class com.sualtikasifi.cizimhafiza.** {
    *** Companion;
}
-keepclasseswithmembers class com.sualtikasifi.cizimhafiza.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Data/domain models decoded from JSON or stored via Room — keep field names.
-keep class com.sualtikasifi.cizimhafiza.data.local.entity.** { *; }
-keep class com.sualtikasifi.cizimhafiza.domain.model.** { *; }
