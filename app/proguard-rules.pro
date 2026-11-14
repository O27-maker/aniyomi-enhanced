-dontobfuscate

# Keep the extension-facing source API ABI intact for dynamically loaded extensions.
# These types are extended, constructed, or invoked from separately compiled extension APKs.
-keep class eu.kanade.tachiyomi.source.Source { public protected *; }
-keep class eu.kanade.tachiyomi.source.Source$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.ConfigurableSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.SourceFactory { public protected *; }
-keep class eu.kanade.tachiyomi.source.UnmeteredSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.FilterList { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.MangasPage { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Page { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SMangaUpdate { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.UpdateStrategy { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.HttpSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.ParsedHttpSource { public protected *; }


-keep,allowoptimization class eu.kanade.**

# Extension APKs subclass HttpSource and override this API method.
# Prevent R8 from devirtualizing it into a final method in release builds.
-keepclassmembers class eu.kanade.tachiyomi.source.online.HttpSource {
    public long getId();
    public eu.kanade.tachiyomi.source.model.FilterList getFilterList();
}
# Extensions reflectively access HttpSource's delegated headers property.
-keepclassmembers class eu.kanade.tachiyomi.source.online.HttpSource {
    private kotlin.Lazy headers$delegate;
}
-keep,allowoptimization class tachiyomi.**
-keep,allowoptimization class mihon.**

# OkHttp's Zstd integration discovers the ZSTD-KMP implementation reflectively.
-keep class com.squareup.zstd.** { *; }

# WorkManager instantiates its generated Room database reflectively.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}

# Keep common dependencies used in extensions
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class android.content.** { *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class android.test.base.** { *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }
-keep,allowoptimization class com.arthenica.** { public protected *; }

# From extensions-lib
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.torrentutils.** { public protected *; }

##---------------Begin: proguard configuration for RxJava 1.x  ----------
-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent
##---------------End: proguard configuration for RxJava 1.x  ----------

##---------------Begin: proguard configuration for okhttp  ----------
-keepclasseswithmembers class okhttp3.MultipartBody$Builder { *; }
##---------------End: proguard configuration for okhttp  ----------

##---------------Begin: proguard configuration for kotlinx.serialization  ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.** # core serialization annotations

# kotlinx-serialization-json specific. Add this if there is java.lang.NoClassDefFoundError kotlinx.serialization.json.JsonObjectSerializer
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclassmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class eu.kanade.**$$serializer { *; }
-keepclassmembers class eu.kanade.** {
    *** Companion;
}
-keepclassmembers class eu.kanade.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.** {
    <methods>;
}
##---------------End: proguard configuration for kotlinx.serialization  ----------

# XmlUtil
-keep public enum nl.adaptivity.xmlutil.EventType { *; }
