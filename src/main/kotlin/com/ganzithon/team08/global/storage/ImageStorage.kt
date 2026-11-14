// 인증 사진 바이너리를 저장·조회·삭제하는 저장소 경계를 정의한다.
package com.ganzithon.team08.global.storage

interface ImageStorage {
    // 저장에 성공하면 조회·삭제에 쓸 저장 키를 반환한다. 원본 파일명은 저장하지 않는다.
    fun save(content: ByteArray, format: ImageFormat): String

    fun load(storageKey: String): ByteArray

    // 이미 삭제된 키를 다시 삭제해도 예외를 던지지 않는다 (탈퇴 재시도에서 반복 호출될 수 있음).
    fun delete(storageKey: String)
}
