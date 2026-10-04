# Third-party notices

Original wonderPlay source is MIT licensed (see LICENSE). The combined application is distributed under GPL-3.0-or-later because it links NewPipe Extractor (see LICENSE-GPL-3.0). The supplied logo was adapted at the project owner’s request. This document identifies direct runtime/build/test dependencies; their own copyright notices and license texts remain applicable. No copyrighted music or album-art catalog is bundled with the app. Material icons are used under Apache-2.0.

| Component | License | Upstream |
| --- | --- | --- |
| NewPipe Extractor v0.26.5 | GPL-3.0-or-later | https://github.com/TeamNewPipe/NewPipeExtractor |
| Rhino | MPL-2.0 | https://github.com/mozilla/rhino |
| Jsoup | MIT | https://github.com/jhy/jsoup |
| Nanojson (NewPipe fork) | MIT | https://github.com/TeamNewPipe/nanojson |
| Protobuf Java Lite | BSD-3-Clause | https://github.com/protocolbuffers/protobuf |
| JDK desugaring library | GPL-2.0 with Classpath Exception | https://github.com/google/desugar_jdk_libs |
| Kotlin and kotlinx.coroutines | Apache-2.0 | https://github.com/JetBrains/kotlin ; https://github.com/Kotlin/kotlinx.coroutines |
| AndroidX Core, Activity, Lifecycle, Compose, Material, Room, DataStore, WorkManager, Palette, SplashScreen | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| AndroidX Media3 / ExoPlayer | Apache-2.0 | https://github.com/androidx/media |
| Coil | Apache-2.0 | https://github.com/coil-kt/coil |
| OkHttp and Okio | Apache-2.0 | https://github.com/square/okhttp ; https://github.com/square/okio |
| Guava / ListenableFuture, Error Prone annotations | Apache-2.0 | https://github.com/google/guava ; https://github.com/google/error-prone |
| Android Gradle Plugin, KSP | Apache-2.0 | https://android.googlesource.com/platform/tools/base/ ; https://github.com/google/ksp |
| Gradle | Apache-2.0 | https://github.com/gradle/gradle |
| JUnit 4 (tests only) | EPL-1.0 | https://github.com/junit-team/junit4 |
| Robolectric (tests only) | MIT | https://github.com/robolectric/robolectric |
| Hamcrest (tests only) | BSD-3-Clause | https://github.com/hamcrest/JavaHamcrest |
| AndroidX Test / Espresso (tests only) | Apache-2.0 | https://github.com/android/android-test |

LRCLIB and lyrics.ovh public APIs provide lyrics at runtime; no song lyrics are bundled. MusicBrainz/Cover Art Archive metadata and artwork remain governed by their respective licenses and artwork owners' rights. Attribution does not imply endorsement or grant additional music redistribution rights.

Apache License 2.0 text: https://www.apache.org/licenses/LICENSE-2.0
Eclipse Public License 1.0 text: https://www.eclipse.org/legal/epl-v10.html

Runtime dependencies also carry their upstream META-INF notices in their published artifacts. Build-only and test-only components are not part of the release runtime. The dependency lock/catalog and Gradle dependency reports can be used to audit resolved versions.
