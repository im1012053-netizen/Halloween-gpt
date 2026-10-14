# Halloween Hunt (Forge 1.21.1)

## jar 만들기
### A) GitHub 로 자동 빌드 (설치 불필요)
1. GitHub 에 새 저장소를 만들고 이 폴더의 모든 파일(.github 폴더 포함)을 올린다.
2. 저장소의 Actions 탭 → build 실행이 끝나면 Artifacts 의 `halloweenhunt-jar` 다운로드 (zip 안에 jar).

### B) 내 PC 에서 빌드
1. Java 21 설치.
2. Forge MDK 1.21.1 에서 `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` 를 이 폴더로 복사
   (또는 Gradle 8.8 을 설치해 `gradle build` 실행).
3. `gradlew build` → `build/libs/halloweenhunt-1.0.0.jar`

## 설치
커스포지 앱: Forge 1.21.1 프로필 → mods 폴더에 jar. 서버와 참가자 모두 동일하게.

## 명령어
/hunt start | stop | reset   (OP 권한)
