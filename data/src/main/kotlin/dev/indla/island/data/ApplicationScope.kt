package dev.indla.island.data

import javax.inject.Qualifier

/** The process-wide `CoroutineScope` (main thread, supervisor job) provided by the app module. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
