package com.pluk.reader.data.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Dónde vive cada libro y su portada en el almacenamiento privado (ADR 0006). */
@Singleton
class LibraryFiles @Inject constructor(@ApplicationContext context: Context) {
    private val root = context.applicationContext.filesDir
    private val booksDir = File(root, "books").apply { mkdirs() }
    private val coversDir = File(root, "covers").apply { mkdirs() }

    fun bookFile(bookId: String) = File(booksDir, "$bookId.epub")
    fun coverFile(bookId: String) = File(coversDir, "$bookId.jpg")
    fun newTempFile(): File = File.createTempFile("import-", ".tmp", booksDir)
}
