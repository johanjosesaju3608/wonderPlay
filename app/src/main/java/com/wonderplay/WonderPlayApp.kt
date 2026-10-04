package com.wonderplay

import android.app.Application
import com.wonderplay.data.LibraryRepository
import com.wonderplay.source.SourceRegistry

class WonderPlayApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val library = LibraryRepository(application)
    val downloads = com.wonderplay.download.DownloadRepository(application, library)
    val sources = SourceRegistry(application, library, downloads)
}
