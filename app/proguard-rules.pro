# Services are referenced from the manifest; Hilt and Room ship their own consumer rules.
-keep class dev.indla.island.service.** { *; }
