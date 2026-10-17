package com.ansh.omnipage

import android.app.Application
import com.ansh.omnipage.data.CoverStore
import com.ansh.omnipage.data.LibraryRepository
import com.ansh.omnipage.data.db.OmniDatabase
import com.ansh.omnipage.data.reader.ReaderRepository
import com.ansh.omnipage.data.reader.ReaderSettingsStore
import com.ansh.omnipage.data.settings.SettingsStore
import com.ansh.omnipage.scan.FolderScanner
import com.ansh.omnipage.scan.MetadataExtractor
import com.ansh.omnipage.scan.ScanScheduler

// Hand-rolled service locator — small enough that a DI framework would be noise. 
class AppContainer(app: Application) {
    val database: OmniDatabase = OmniDatabase.create(app)
    val settings = SettingsStore(app)
    val covers = CoverStore(app)
    val scanner = FolderScanner(app)
    val extractor = MetadataExtractor(app)
    val repository = LibraryRepository(app, database, scanner, extractor, covers)
    val scanScheduler = ScanScheduler(app)

    // Phase 2
    val readerSettings = ReaderSettingsStore(app)
    val reader = ReaderRepository(database, repository)
}
