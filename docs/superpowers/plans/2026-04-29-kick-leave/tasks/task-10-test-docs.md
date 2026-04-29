### Task 10: 전체 테스트 + features.md 갱신

**Files:**
- Modify: `docs/ai/features.md:58-59`

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: ALL PASS

- [ ] **Step 2: features.md 갱신**

`docs/ai/features.md`에서 멤버 관리 섹션의 두 항목을 갱신:

```markdown
| `[x]` | 멤버 추방 | HOST가 특정 멤버 추방 (HOST는 추방 불가) | room_members |
| `[x]` | 방 나가기 | 본인이 방에서 탈퇴 | room_members |
```

- [ ] **Step 3: 커밋**

```bash
git add docs/ai/features.md
git commit -m "docs: features.md 멤버 추방 및 방 나가기 완료 표기"
```
