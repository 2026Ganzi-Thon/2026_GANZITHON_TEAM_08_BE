// 로컬 디스크 이미지 저장소의 저장·조회·삭제와 경로 조작 방어를 검증한다.
package com.ganzithon.team08.global.storage

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.nio.file.Path

class LocalImageStorageTests {
    @Test
    fun savedImageCanBeLoadedBackByItsStorageKey(@TempDir tempDir: Path) {
        val storage = LocalImageStorage(tempDir.toString())
        val content = byteArrayOf(1, 2, 3, 4)

        val storageKey = storage.save(content, ImageFormat.PNG)

        assertArrayEquals(content, storage.load(storageKey))
    }

    @Test
    fun storageKeyDoesNotContainOriginalFileNameAndUsesDetectedExtension(@TempDir tempDir: Path) {
        val storage = LocalImageStorage(tempDir.toString())

        val storageKey = storage.save(byteArrayOf(1), ImageFormat.WEBP)

        assertFalse(storageKey.contains("원본"))
        assert(storageKey.endsWith(".webp"))
    }

    @Test
    fun loadingMissingKeyThrows(@TempDir tempDir: Path) {
        val storage = LocalImageStorage(tempDir.toString())

        assertThrows<FileNotFoundException> { storage.load("missing.jpg") }
    }

    @Test
    fun deleteRemovesFileAndIsIdempotent(@TempDir tempDir: Path) {
        val storage = LocalImageStorage(tempDir.toString())
        val storageKey = storage.save(byteArrayOf(1), ImageFormat.JPEG)

        storage.delete(storageKey)
        storage.delete(storageKey)

        assertThrows<FileNotFoundException> { storage.load(storageKey) }
    }

    @Test
    fun pathTraversalInStorageKeyIsRejected(@TempDir tempDir: Path) {
        val storage = LocalImageStorage(tempDir.toString())

        assertThrows<IllegalArgumentException> { storage.load("../escape.jpg") }
        assertThrows<IllegalArgumentException> { storage.delete("../escape.jpg") }
    }
}
