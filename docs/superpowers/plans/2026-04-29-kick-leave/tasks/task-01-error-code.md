### Task 1: ErrorCode 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/common/error/ErrorCode.java:30-32`

- [ ] **Step 1: ErrorCode에 3개 에러코드 추가**

`ErrorCode.java`의 `// 403 FORBIDDEN` 섹션(30행 부근)에 추가:

```java
// 403 FORBIDDEN
NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "방의 멤버가 아닙니다"),
NOT_ROOM_HOST(HttpStatus.FORBIDDEN, "호스트 권한이 필요합니다"),
CANNOT_KICK_HOST(HttpStatus.FORBIDDEN, "호스트는 추방할 수 없습니다"),
CANNOT_LEAVE_AS_HOST(HttpStatus.FORBIDDEN, "호스트는 방을 나갈 수 없습니다"),
```

`// 400 BAD REQUEST` 섹션에 추가:

```java
KICK_TARGET_NOT_MEMBER(HttpStatus.BAD_REQUEST, "추방 대상이 멤버가 아닙니다"),
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/common/error/ErrorCode.java
git commit -m "feat: 추방/탈퇴 ErrorCode 3개 추가"
```
