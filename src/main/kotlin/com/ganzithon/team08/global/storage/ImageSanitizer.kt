// 인증 이미지의 방향을 유지하면서 개인정보 메타데이터를 제거한다.
package com.ganzithon.team08.global.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

fun interface ImageSanitizer {
    fun sanitize(content: ByteArray, format: ImageFormat): ByteArray
}

class InvalidImageException : RuntimeException()

@Component
class PillowImageSanitizer(
    @Value("\${app.image.python:python3}") private val python: String,
    @Value("\${app.image.max-pixels:24000000}") private val maxPixels: Int,
) : ImageSanitizer {
    private val slots = Semaphore(1, true)
    private val script = requireNotNull(javaClass.getResourceAsStream("/storage/sanitize_image.py")).bufferedReader().use { it.readText() }

    override fun sanitize(content: ByteArray, format: ImageFormat): ByteArray {
        check(slots.tryAcquire(15, TimeUnit.SECONDS)) { "이미지 처리 대기 시간이 초과됐습니다." }
        try {
            val output = Files.createTempFile("mindot-image-", ".tmp")
            try {
                val process = ProcessBuilder(python, "-I", "-c", script, format.name, maxPixels.toString())
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                try {
                    // 원본 이미지는 파일로 보관하지 않고 표준 입력으로 전달한다.
                    Thread.startVirtualThread {
                        try {
                            process.outputStream.use { it.write(content) }
                        } catch (_: IOException) {
                            // 변환기가 먼저 종료한 경우 아래에서 종료 코드로 판단한다.
                        }
                    }
                    if (!process.waitFor(20, TimeUnit.SECONDS)) throw InvalidImageException()
                    if (process.exitValue() == 2) throw InvalidImageException()
                    check(process.exitValue() == 0) { "이미지 처리기를 실행할 수 없습니다." }
                    val sanitized = Files.readAllBytes(output)
                    check(ImageFormat.detect(sanitized) == format) { "저장 이미지 형식이 일치하지 않습니다." }
                    return sanitized
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                }
            } finally {
                Files.deleteIfExists(output)
            }
        } finally {
            slots.release()
        }
    }
}
