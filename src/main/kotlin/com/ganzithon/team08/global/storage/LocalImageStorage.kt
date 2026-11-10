// IMAGE_STORAGE_DIR 아래 로컬 디스크에 이미지를 저장하는 ImageStorage 첫 구현이다.
package com.ganzithon.team08.global.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Component
class LocalImageStorage(
    @Value("\${app.image.storage-dir}") storageDir: String,
) : ImageStorage {
    private val root: Path = Path.of(storageDir).toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    override fun save(content: ByteArray, format: ImageFormat): String {
        val storageKey = "${UUID.randomUUID()}.${format.extension}"
        Files.write(resolve(storageKey), content)
        return storageKey
    }

    override fun load(storageKey: String): ByteArray {
        val path = resolve(storageKey)
        if (!Files.isRegularFile(path)) throw FileNotFoundException("이미지를 찾을 수 없습니다: $storageKey")
        return Files.readAllBytes(path)
    }

    override fun delete(storageKey: String) {
        Files.deleteIfExists(resolve(storageKey))
    }

    // storageKey는 서버가 UUID로 발급하지만, 경로 조작(../)으로 저장 루트를 벗어나지 못하도록 한 번 더 막는다.
    private fun resolve(storageKey: String): Path {
        val resolved = root.resolve(storageKey).normalize()
        require(resolved.startsWith(root)) { "잘못된 저장 키입니다: $storageKey" }
        return resolved
    }
}
