### Task 9: Controller 엔드포인트 추가

**Files:**
- Modify: `src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java`

**선행 조건:** Task 4 (kick), Task 5 (leave) 완료 필요

- [ ] **Step 1: leaveRoom 엔드포인트 추가**

`RoomController.java`의 `getMembers()` 메서드 아래에 추가. **`/members/me`를 `/members/{userId}`보다 먼저 선언:**

```java
@Operation(summary = "방 나가기", description = "방에서 나갑니다. MEMBER만 가능합니다. HOST는 나갈 수 없습니다.")
@DeleteMapping("/{roomId}/members/me")
public ResponseEntity<Void> leaveRoom(
        @AuthenticationPrincipal Long userId,
        @PathVariable UUID roomId
) {
    roomMemberService.leave(roomId, userId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 2: kickMember 엔드포인트 추가**

`leaveRoom()` 아래에 추가:

```java
@Operation(summary = "멤버 추방", description = "방에서 멤버를 추방합니다. HOST만 가능합니다.")
@DeleteMapping("/{roomId}/members/{userId}")
public ResponseEntity<Void> kickMember(
        @AuthenticationPrincipal Long hostUserId,
        @PathVariable UUID roomId,
        @PathVariable Long userId
) {
    roomMemberService.kick(roomId, userId, hostUserId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/com/howaboutus/backend/rooms/controller/RoomController.java
git commit -m "feat: DELETE /rooms/{roomId}/members/me, DELETE /rooms/{roomId}/members/{userId} 엔드포인트 추가"
```
