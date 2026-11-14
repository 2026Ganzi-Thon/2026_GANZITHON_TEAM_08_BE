// 파일 시그니처(매직 넘버)로 정지 이미지 형식을 판별한다.
package com.ganzithon.team08.global.storage

enum class ImageFormat(val extension: String) {
    JPEG("jpg"),
    PNG("png"),
    WEBP("webp"),
    ;

    companion object {
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_SIGNATURE =
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val RIFF_SIGNATURE = byteArrayOf(0x52, 0x49, 0x46, 0x46) // "RIFF"
        private val WEBP_SIGNATURE = byteArrayOf(0x57, 0x45, 0x42, 0x50) // "WEBP"

        // 확장자가 아니라 실제 바이트 내용으로 형식을 판별한다 (요청 확장자 위조 방지).
        fun detect(content: ByteArray): ImageFormat? = when {
            content.startsWith(JPEG_SIGNATURE) -> JPEG
            content.startsWith(PNG_SIGNATURE) -> PNG
            content.size >= 12 && content.startsWith(RIFF_SIGNATURE) &&
                content.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE) -> WEBP
            else -> null
        }

        private fun ByteArray.startsWith(signature: ByteArray): Boolean =
            size >= signature.size && copyOfRange(0, signature.size).contentEquals(signature)

        // 저장 키의 확장자로 이미지를 다시 서빙할 때 Content-Type을 정하는 데 쓴다.
        fun fromExtension(extension: String): ImageFormat? = entries.firstOrNull { it.extension == extension }
    }
}
