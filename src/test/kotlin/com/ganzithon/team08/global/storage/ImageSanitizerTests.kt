// 사진의 민감한 메타데이터 제거와 방향·애니메이션 보존을 검증한다.
package com.ganzithon.team08.global.storage

import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageSanitizerTests {
    private val python = System.getenv("IMAGE_PYTHON_EXECUTABLE") ?: "python3"
    private val sanitizer = PillowImageSanitizer(python, 24_000_000)

    @Test
    fun `GPS와 방향이 있는 JPEG의 픽셀을 회전하고 EXIF를 비운다`() {
        val original = fixture("gps-orientation.jpg")
        verify(original, "assert im.size == (12, 8) and im.getexif().get_ifd(34853)")
        val cleaned = sanitizer.sanitize(original, ImageFormat.JPEG)

        assertEquals(ImageFormat.JPEG, ImageFormat.detect(cleaned))
        verify(cleaned, "assert im.size == (8, 12) and not im.getexif()")
    }

    @Test
    fun `PNG와 WebP는 투명 프레임과 반복을 유지하고 EXIF와 텍스트를 제거한다`() {
        for (format in listOf(ImageFormat.PNG, ImageFormat.WEBP)) {
            val cleaned = sanitizer.sanitize(fixture("animated.${format.extension}"), format)
            assertEquals(format, ImageFormat.detect(cleaned))
            verify(cleaned, """
                assert im.n_frames == 2 and im.info['loop'] == 3
                assert im.size == (8, 12) and not im.getexif()
                assert not {'Description', 'xmp', 'exif'} & im.info.keys()
                assert im.convert('RGBA').getpixel((0, 0))[3] == 128
                im.seek(1)
                assert im.convert('RGBA').getpixel((0, 0))[3] == 100
                assert round(im.info['duration']) == 160
            """.trimIndent())
        }
    }

    @Test
    fun `헤더만 있는 파일과 픽셀 제한 초과를 거부한다`() {
        assertFailsWith<InvalidImageException> {
            sanitizer.sanitize(byteArrayOf(-1, -40, -1, 1), ImageFormat.JPEG)
        }
        assertFailsWith<InvalidImageException> {
            PillowImageSanitizer(python, 32).sanitize(fixture("gps-orientation.jpg"), ImageFormat.JPEG)
        }
    }

    private fun fixture(name: String) = requireNotNull(javaClass.getResourceAsStream("/privacy/$name")).use { it.readAllBytes() }

    private fun verify(bytes: ByteArray, checks: String) {
        val program = "import io, sys\nfrom PIL import Image\nim = Image.open(io.BytesIO(sys.stdin.buffer.read()))\nim.load()\n$checks"
        val process = ProcessBuilder(python, "-I", "-c", program).redirectErrorStream(true).start()
        try {
            process.outputStream.use { it.write(bytes) }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
