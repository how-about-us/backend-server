---
name: notion-sync
description: >
  Use when syncing local spec changes to Notion. Reads docs/specs/_index.md,
  detects status changes and newly added spec files, then updates the Notion
  todoList database (TodoList page) via MCP — one-way, Local → Notion only.
  Make sure to use this skill whenever the user says "Notion 업데이트", "Notion 동기화",
  "sync", "_index 반영", "스펙 상태 올려줘", or after any changes to docs/specs/ files.
---

# Notion Sync — Spec Index Synchronizer

## 역할

`docs/specs/_index.md`를 진실의 원천으로 삼아, 변경된 상태와 새로 추가된 스펙을 Notion todoList 데이터베이스에 단방향(Local → Notion)으로 반영하는 동기화 에이전트.

---

## 핵심 원칙

1. **단방향 동기화만 수행한다** — Notion은 팀 공유 뷰어일 뿐, Source of Truth는 로컬 파일이다. Notion의 값을 로컬로 끌어오는 역방향 작업은 절대 하지 않는다.
2. **Spec ID가 식별자다** — Notion 페이지와 로컬 항목의 매핑은 `Spec ID` 속성(예: `FE-001-01`)으로만 판단한다. 이름이 달라도 Spec ID가 같으면 동일 항목이다.
3. **변경된 것만 업데이트한다** — 전체 재작성이 아니라 diff 기반으로 동작한다. 불필요한 API 호출을 줄이고, 이미 올바른 항목은 건드리지 않는다.
4. **완료 항목([x])은 상태를 '완료'로 올린다** — 로컬에서 체크박스가 `[x]`로 바뀐 항목은 Notion 상태를 `완료`로 업데이트한다.

---

## 동기화 절차

### Step 1: 로컬 상태 파싱

`docs/specs/_index.md`를 읽어 모든 spec 항목을 추출한다.

**파싱 규칙:**

| 패턴 | 의미 |
|------|------|
| `- [x] [FE-001-01](...)` | 상태: `완료` |
| `` - [ ] [ID](...) — 제목 `진행중` `` | 상태: `진행중` |
| `` - [ ] [ID](...) — 제목 `보류` `` | 상태: `보류` |
| `- [ ] [ID](...) — 제목` (태그 없음) | 상태: `대기` |

**섹션 헤더에서 추출:**
- `` ### FE-001: 인증 (Auth) `P1` `` → 카테고리: `인증`, 우선순위: `P1`
- INFRA/REFACTOR 접두사 항목은 별도 섹션 없이 최상위에 기재됨

**타입 규칙:**
- ID가 `FE-`로 시작 → 타입: `FE`
- ID가 `INFRA-`로 시작 → 타입: `INFRA`
- ID가 `REFACTOR-`로 시작 → 타입: `REFACTOR`

**카테고리 매핑 (FE):**

| 그룹 ID | 카테고리 |
|---------|---------|
| FE-001 | 인증 |
| FE-002 | 여행방 |
| FE-003 | 멤버관리 |
| FE-004 | 장소 |
| FE-005 | 보관함 |
| FE-006 | 일정 |
| FE-007 | 채팅 |
| FE-008 | AI기능 |

INFRA/REFACTOR 항목의 카테고리는 타입과 동일하게 설정한다 (예: `INFRA`).

---

### Step 2: 새 Spec 파일 감지

`docs/specs/` 하위 디렉토리에서 `.md` 파일 목록을 스캔하여, Step 1에서 파싱한 항목 목록에 없는 파일이 있는지 확인한다.

- 새 파일의 메타 섹션(`## 메타`)에서 상태, 우선순위, 담당자를 읽어 항목 목록에 추가한다.
- 메타 섹션이 없으면 상태 `대기`로 처리한다.

---

### Step 3: Notion 현재 상태 조회

Notion MCP로 todoList 데이터베이스의 현재 항목을 조회한다.

- **데이터 소스**: `collection://33cfd464-a7b8-808c-8f75-000b01d8e382`
- **식별자**: `Spec ID` 속성

`notion-search` 또는 개별 `notion-fetch`로 각 Spec ID에 해당하는 페이지 URL을 수집한다.

---

### Step 4: Diff 계산 및 반영

로컬 파싱 결과와 Notion 현재 상태를 비교하여 세 가지 작업을 수행한다.

#### 4-a. 상태 변경 항목 업데이트

로컬 상태와 Notion 상태가 다른 항목만 `notion-update-page`로 수정한다.

업데이트 대상 속성: `상태`, `우선순위` (변경된 경우)

#### 4-b. 신규 항목 추가

Notion에 없는 Spec ID를 가진 항목은 `notion-create-pages`로 추가한다.

```
parent: { data_source_id: "33cfd464-a7b8-808c-8f75-000b01d8e382" }
properties:
  이름: <제목>
  Spec ID: <ID>
  상태: <상태>
  우선순위: <우선순위>
  카테고리: <카테고리>
  타입: <타입>
```

#### 4-c. 작업 요약 출력

모든 작업이 끝나면 아래 형식으로 결과를 출력한다.

```
✅ Notion 동기화 완료

업데이트: N개
  - FE-001-01: 대기 → 진행중
  - INFRA-001: 진행중 → 완료

신규 추가: N개
  - FE-009-01: 새 spec 항목

변경 없음: N개
```

---

## Notion MCP 도구 사용 가이드

| 작업 | 도구 |
|------|------|
| 현재 항목 조회 | `notion-search` (data_source_url 파라미터 사용) |
| 페이지 속성 확인 | `notion-fetch` |
| 상태/속성 업데이트 | `notion-update-page` |
| 신규 항목 생성 | `notion-create-pages` |

---

## 금지 사항

| 금지 행동 | 이유 |
|-----------|------|
| Notion → 로컬 역방향 동기화 | Source of Truth는 로컬 파일이므로, Notion 값이 더 최신이더라도 덮어쓰지 않는다 |
| 전체 삭제 후 재생성 | 기존 Notion 페이지에 메모/댓글이 달려 있을 수 있으므로, 반드시 기존 페이지를 업데이트한다 |
| Spec ID 없이 이름만으로 매핑 | 이름은 변경될 수 있으므로 Spec ID만 식별자로 사용한다 |
| `_index.md` 수정 | 이 스킬은 읽기 전용이다. 로컬 파일은 변경하지 않는다 |

---

## 참고

- **todoList 데이터베이스**: `collection://33cfd464-a7b8-808c-8f75-000b01d8e382`
- **TodoList 페이지**: `https://www.notion.so/33cfd464a7b8809b8e4adfa19e04fd90`
- **Spec 인덱스**: `docs/specs/_index.md`
- **상태값**: `대기` / `진행중` / `완료` / `보류`
