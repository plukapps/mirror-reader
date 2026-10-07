package com.pluk.reader.data.library

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Copia [input] en [output] y devuelve el SHA-256 del contenido en hexadecimal (LIB-003). */
fun copyWithSha256(input: InputStream, output: OutputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
