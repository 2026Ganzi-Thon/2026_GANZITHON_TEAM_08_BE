// 파일 시그니처 기반 이미지 형식 판별 로직을 검증한다.
package com.ganzithon.team08.global.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageFormatTests {
    @Test
    fun detectsJpegBySignature() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01)
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(bytes))
    }

    @Test
    fun detectsPngBySignature() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        assertEquals(ImageFormat.PNG, ImageFormat.detect(bytes))
    }

    @Test
    fun detectsWebpByRiffAndWebpMarkers() {
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(bytes))
    }

    @Test
    fun rejectsRiffContainerThatIsNotWebp() {
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20) // RIFF...AVI
        assertNull(ImageFormat.detect(bytes))
    }

    @Test
    fun rejectsExtensionSpoofingWithUnknownContent() {
        val bytes = "그냥 텍스트 파일입니다".toByteArray()
        assertNull(ImageFormat.detect(bytes))
    }

    @Test
    fun rejectsContentShorterThanAnySignature() {
        assertNull(ImageFormat.detect(byteArrayOf(0x01, 0x02)))
    }
}
