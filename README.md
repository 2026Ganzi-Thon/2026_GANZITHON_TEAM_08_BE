# 2026 GANZITHON Team 08 Backend

Kotlin과 Spring Boot 기반 백엔드 프로젝트입니다.

## 개발 환경

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Kotlin | 2.3.21 |
| Spring Boot | 4.1.2 |
| Gradle Wrapper | 9.3.0 |

JDK 21을 설치하고 `JAVA_HOME`이 해당 JDK를 가리키도록 설정합니다. Gradle은 별도로 설치할 필요가 없습니다. 처음 빌드할 때 Wrapper와 의존성을 내려받으므로 인터넷 연결이 필요합니다.

Kotlin JPA 플러그인을 사용해 엔티티의 기본 생성자와 프록시 지원을 구성합니다.

macOS에서는 다음과 같이 JDK를 선택할 수 있습니다.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

IntelliJ IDEA에서는 프로젝트 루트를 열고 Project SDK와 Gradle JVM을 모두 Java 21로 설정합니다.

## 실행

MySQL 서버에 `ganzithon` 데이터베이스를 먼저 생성합니다. DB 계정에는 해당 데이터베이스의 테이블 생성·변경 및 데이터 조회·수정 권한이 필요합니다.

```sql
CREATE DATABASE ganzithon CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

접속 계정과 비밀번호를 환경변수로 설정하고 실행합니다. 아래 예시 값은 실제 MySQL 계정 정보로 바꿉니다.

```sh
export DB_USERNAME='your_mysql_username'
export DB_PASSWORD='your_mysql_password'
./gradlew bootRun
```

기본 프로필은 `local`이며 MySQL을 사용합니다. 기본 접속 주소는 `jdbc:mysql://localhost:3306/ganzithon`입니다. 다른 서버나 데이터베이스를 사용하려면 `DB_URL` 환경변수를 지정합니다.

```sh
export DB_URL='jdbc:mysql://localhost:3306/ganzithon'
```

Hibernate의 `ddl-auto: update` 설정으로 실행 시 엔티티에 맞춰 테이블을 생성하거나 변경합니다. 애플리케이션 종료 시 테이블을 삭제하지 않습니다. 데이터베이스 자체와 접속 계정은 자동으로 생성하지 않습니다.

기본 포트는 `8080`입니다. 포트를 바꾸려면 `SERVER_PORT` 환경변수를 지정합니다.

```sh
SERVER_PORT=8081 ./gradlew bootRun
```

상태 확인 API는 다음과 같이 호출합니다.

```sh
curl http://localhost:8080/actuator/health
```

정상 실행 시 HTTP 200을 반환하고 JSON의 `status`가 `UP`이 됩니다. 응답에는 상태 점검 그룹 목록이 함께 포함될 수 있습니다. Actuator는 `health`만 HTTP로 공개하며 내부 상세 정보는 응답에 포함하지 않습니다. 업무용 API와 루트(`/`) 페이지는 아직 없습니다.

## 테스트와 빌드

```sh
./gradlew test
./gradlew clean build
java -jar build/libs/ganzithon-team08-0.0.1-SNAPSHOT.jar
```

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다. 테스트는 `test` 프로필의 H2 메모리 DB와 `create-drop` 설정을 사용하므로 MySQL 서버나 접속 환경변수 없이 실행할 수 있습니다. 전체 Spring 컨텍스트의 상태 확인 API와 JPA 저장·조회, 지연 로딩, 변경 감지, 중복 제약을 검증합니다. 테스트 결과는 `build/reports/tests/test/index.html`에서 확인합니다. JAR 실행에는 위의 MySQL 접속 설정이 필요합니다.

GitHub Actions는 PR과 `main` 브랜치 push 시 Java 21에서 `clean build`를 실행하며, 수동 실행도 지원합니다. 워크플로는 저장소에 반영된 이후 실행됩니다.

## 기본 구성

- Spring MVC: REST API와 내장 Tomcat을 제공합니다.
- Validation: 요청 DTO의 입력값 검증을 지원합니다.
- Jackson Kotlin: Kotlin 객체와 JSON을 변환합니다.
- Actuator: 애플리케이션 상태를 확인합니다.
- Spring Data JPA: Hibernate를 통한 엔티티 매핑을 지원합니다.
- MySQL Connector/J: 애플리케이션에서 MySQL에 연결하는 JDBC 드라이버입니다.
- H2: 테스트 전용 메모리 DB입니다. 실행용 JAR에는 포함하지 않습니다.
- JUnit과 Spring Boot Test: 테스트를 실행합니다.

```text
src/
├── main/
│   ├── kotlin/com/ganzithon/team08/
│   │   ├── GanzithonTeam08Application.kt
│   │   └── domain/
│   │       ├── certification/
│   │       ├── cheer/
│   │       ├── cleanup/
│   │       ├── common/
│   │       ├── group/
│   │       ├── mission/
│   │       ├── onboarding/
│   │       └── user/
│   └── resources/
│       ├── application.yml
│       └── application-local.yml
└── test/
    ├── kotlin/com/ganzithon/team08/
    │   ├── GanzithonTeam08ApplicationTests.kt
    │   └── domain/
    └── resources/application-test.yml
```

새 기능은 `com.ganzithon.team08` 아래에 추가하면 기본 컴포넌트 스캔에 포함됩니다. 현재는 엔티티, Spring Data JPA Repository와 상태 확인 API를 제공하며, 업무용 API·서비스·카카오 인증·이미지 저장·재시도 작업 실행기는 아직 구현하지 않았습니다.

공통 설정은 `application.yml`에서 관리합니다. 비밀번호와 API 키는 환경변수 등 외부 설정으로 주입하며 저장소에 기록하지 않습니다. `.env` 파일은 Git에서 제외되지만 Spring Boot가 자동으로 읽지는 않습니다. 실행 환경이나 IDE에서 환경변수를 설정해야 합니다.

## DB 설정

| 설정 | 값 |
| --- | --- |
| 기본 DB | MySQL. |
| `DB_URL` | `local` 프로필의 JDBC URL. 기본값은 `jdbc:mysql://localhost:3306/ganzithon`. |
| `DB_USERNAME` | `local` 프로필의 DB 계정. 필수. |
| `DB_PASSWORD` | `local` 프로필의 DB 비밀번호. 필수. |
| 공통 `ddl-auto` | `update`. |
| 테스트 DB | H2 메모리 DB, `create-drop`. |

`SPRING_PROFILES_ACTIVE`로 별도 프로필을 사용하면 해당 프로필에 접속 설정을 추가하거나 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 환경변수로 지정합니다. 공통 테이블 생성 방식은 `update`를 사용합니다. 별도 마이그레이션 도구는 추가하지 않았습니다.

`update`는 스키마 변경 이력을 관리하지 않으며 컬럼 이름 변경이나 데이터 변환까지 처리하지 않습니다. 이런 변경은 별도 SQL로 처리해야 합니다. H2 테스트 통과가 실제 MySQL에서의 호환성 검증을 대신하지는 않습니다.

## 버전 기준

[Spring Initializr](https://start.spring.io/)의 Kotlin 프로젝트를 바탕으로 구성했습니다. Java와 빌드 도구의 기준은 [Spring Boot 시스템 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)을 따르며, Gradle은 Kotlin 2.3.21의 [공식 호환 범위](https://kotlinlang.org/docs/gradle-configure-project.html)에 맞춰 9.3.0으로 고정했습니다. Wrapper 배포 파일의 SHA-256 검증도 활성화했습니다.
