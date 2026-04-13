# review-code-against-docs 스킬 업그레이드 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `review-code-against-docs` 스킬에 코드 구조 검사(패키지 경로 + 레이어 의존성 방향)를 Step 1로 추가하고, 보고서 섹션 번호를 새 구조에 맞게 업데이트한다.

**Architecture:** 단일 파일(`.claude/skills/review-code-against-docs/SKILL.md`) 수정. 기존 Step 1~3을 Step 2~4로 번호 변경하고, 새 Step 1을 삽입한다. 보고서 예시도 새 섹션 번호 체계로 맞춘다.

**Tech Stack:** Markdown (스킬 파일)

---

## 파일 맵

| 구분 | 경로 |
|------|------|
| Modify | `.claude/skills/review-code-against-docs/SKILL.md` |

---

### Task 1: frontmatter description 업데이트

**Files:**
- Modify: `.claude/skills/review-code-against-docs/SKILL.md:1-4`

- [ ] **Step 1: description 필드를 새 기능을 반영하도록 수정한다**

기존:
```markdown
---
name: review-code-against-docs
description: Before opening a PR, checks that changed code (git diff main) aligns with CONTRIBUTING.md conventions, features.md specs, and erd.md structure
---
```

변경 후:
```markdown
---
name: review-code-against-docs
description: Before opening a PR, checks that changed code (git diff main) aligns with package structure, layer dependencies, CONTRIBUTING.md conventions, features.md specs, and erd.md structure
---
```

- [ ] **Step 2: 파일을 저장하고 내용을 확인한다**

```bash
head -5 .claude/skills/review-code-against-docs/SKILL.md
```

Expected:
```
---
name: review-code-against-docs
description: Before opening a PR, checks that changed code (git diff main) aligns with package structure, layer dependencies, CONTRIBUTING.md conventions, features.md specs, and erd.md structure
---
```

---

### Task 2: Checklist 기존 Step 번호 변경 (Step 1→2, Step 2→3, Step 3→4)

**Files:**
- Modify: `.claude/skills/review-code-against-docs/SKILL.md`

- [ ] **Step 1: `### Step 1. 컨벤션 검사`를 `### Step 2. 컨벤션 검사`로 변경한다**

기존:
```markdown
### Step 1. 컨벤션 검사
```

변경 후:
```markdown
### Step 2. 컨벤션 검사
```

- [ ] **Step 2: `### Step 2. 스펙 정합성 검사`를 `### Step 3. 스펙 정합성 검사`로 변경한다**

기존:
```markdown
### Step 2. 스펙 정합성 검사
```

변경 후:
```markdown
### Step 3. 스펙 정합성 검사
```

- [ ] **Step 3: `### Step 3. ERD 정합성 검사`를 `### Step 4. ERD 정합성 검사`로 변경한다**

기존:
```markdown
### Step 3. ERD 정합성 검사
```

변경 후:
```markdown
### Step 4. ERD 정합성 검사
```

Step 4 조건문도 함께 업데이트한다:

기존:
```markdown
`entity/` 경로 하위 파일이 변경된 경우에만 실행한다. 없으면 `[건너뜀]`으로 표시하고 Step 3을 종료한다.
```

변경 후:
```markdown
`entity/` 경로 하위 파일이 변경된 경우에만 실행한다. 없으면 `[건너뜀]`으로 표시하고 Step 4를 종료한다.
```

- [ ] **Step 4: 변경 내용을 확인한다**

```bash
grep -n "### Step" .claude/skills/review-code-against-docs/SKILL.md
```

Expected:
```
14:### Step 0. diff 수집
XX:### Step 2. 컨벤션 검사
XX:### Step 3. 스펙 정합성 검사
XX:### Step 4. ERD 정합성 검사
```

(Step 1이 없는 상태 — 다음 Task에서 삽입됨)

---

### Task 3: 새 Step 1 — 코드 구조 검사 삽입

**Files:**
- Modify: `.claude/skills/review-code-against-docs/SKILL.md`

- [ ] **Step 1: Step 0 섹션 바로 뒤, Step 2 섹션 바로 앞에 새 Step 1 내용을 삽입한다**

삽입할 내용:

```markdown
### Step 1. 코드 구조 검사

변경된 `src/main/` 하위 `.java` 파일을 대상으로 한다. 없으면 `[건너뜀]`으로 표시하고 Step 1을 종료한다.

#### 1-1. 패키지 경로 검사

변경된 파일의 경로가 `CONTRIBUTING.md`에 정의된 패키지 구조를 따르는지 확인한다.

허용 구조:
```
com.howaboutus.backend.
├── common/
│   ├── config/
│   ├── error/
│   └── integration/
└── <domain>/
    ├── controller/
    ├── service/
    ├── repository/
    └── entity/
```

위 구조에 해당하지 않는 경로에 파일이 위치하면 `[위반]`으로 보고한다.

#### 1-2. 레이어 의존성 방향 검사

변경된 파일의 `import` 문을 분석해 다음 역방향 참조를 탐지한다.

| 위반 패턴 | 설명 |
|---|---|
| `controller/` 파일이 `repository/` 클래스를 import | Controller → Repository 직접 참조 |
| `service/` 파일이 `controller/` 또는 `controller/dto/` 하위 클래스를 import | Service → Controller DTO 역참조 |
| `repository/` 파일이 `service/` 또는 `controller/` 하위 클래스를 import | Repository → 상위 레이어 참조 |

`common/` 하위 클래스는 어느 레이어에서 참조해도 허용한다.
```

- [ ] **Step 2: 삽입 결과를 확인한다**

```bash
grep -n "### Step" .claude/skills/review-code-against-docs/SKILL.md
```

Expected:
```
14:### Step 0. diff 수집
XX:### Step 1. 코드 구조 검사
XX:### Step 2. 컨벤션 검사
XX:### Step 3. 스펙 정합성 검사
XX:### Step 4. ERD 정합성 검사
```

---

### Task 4: 보고서 포맷 예시 업데이트

**Files:**
- Modify: `.claude/skills/review-code-against-docs/SKILL.md`

- [ ] **Step 1: Report Format 섹션의 보고서 출력 예시를 교체한다**

기존 예시 블록:

```markdown
```
## Code-Docs Review

### 1. 컨벤션 (프로덕션)
- [위반] PlaceController: @RequiredArgsConstructor 미사용 (CONTRIBUTING.md: "일반적인 생성자 주입은 @RequiredArgsConstructor를 우선 사용한다")
- [통과] 코드 depth

### 1. 컨벤션 (테스트)
- [위반] PlaceControllerTest: @DisplayName 누락 (CONTRIBUTING.md: "테스트 메서드에는 반드시 @DisplayName을 사용해...")
- [통과] 중복 제거

### 2. 스펙 정합성
- [통과] 장소 검색 API — features.md 스펙과 일치
- [주의] features.md의 장소 검색 항목이 [ ] 상태 — 구현 완료 여부 확인 필요

### 3. ERD 정합성
- [건너뜀] 엔티티 변경 없음

N개 이슈 발견
```
```

변경 후 예시 블록:

```markdown
```
## Code-Docs Review

### 1. 코드 구조
- [위반] PlaceService: controller/dto/PlaceSearchResponse를 import — service → controller DTO 역참조 (CONTRIBUTING.md: 패키지 구조)
- [통과] 패키지 경로
- [통과] 레이어 의존성 방향

### 2-1. 컨벤션 (프로덕션)
- [위반] PlaceController: @RequiredArgsConstructor 미사용 (CONTRIBUTING.md: "일반적인 생성자 주입은 @RequiredArgsConstructor를 우선 사용한다")
- [통과] 코드 depth

### 2-2. 컨벤션 (테스트)
- [위반] PlaceControllerTest: @DisplayName 누락 (CONTRIBUTING.md: "테스트 메서드에는 반드시 @DisplayName을 사용해...")
- [통과] 중복 제거

### 3. 스펙 정합성
- [통과] 장소 검색 API — features.md 스펙과 일치
- [주의] features.md의 장소 검색 항목이 [ ] 상태 — 구현 완료 여부 확인 필요

### 4. ERD 정합성
- [건너뜀] 엔티티 변경 없음

N개 이슈 발견
```
```

- [ ] **Step 2: 변경 내용을 확인한다**

```bash
grep -n "### [0-9]" .claude/skills/review-code-against-docs/SKILL.md
```

Expected (보고서 예시 내부):
```
XX:### 1. 코드 구조
XX:### 2-1. 컨벤션 (프로덕션)
XX:### 2-2. 컨벤션 (테스트)
XX:### 3. 스펙 정합성
XX:### 4. ERD 정합성
```

---

### Task 5: 최종 검증 및 커밋

**Files:**
- Modify: `.claude/skills/review-code-against-docs/SKILL.md`

- [ ] **Step 1: 전체 파일을 읽어 구조를 최종 확인한다**

체크 항목:
- frontmatter description이 코드 구조 검사를 언급하는가?
- Checklist에 Step 0, 1, 2, 3, 4가 순서대로 존재하는가?
- Step 1에 1-1(패키지 경로)과 1-2(레이어 의존성) 소섹션이 있는가?
- Step 1-2에 `[위반]` 패턴 테이블이 있는가?
- Step 1-2에 `common/` 허용 규칙이 명시됐는가?
- 보고서 예시에 `### 1. 코드 구조`가 가장 먼저 나오는가?
- 보고서 예시에 `### 2-1`, `### 2-2`가 있는가?
- 보고서 예시에 `### 3. 스펙 정합성`, `### 4. ERD 정합성`이 있는가?

- [ ] **Step 2: 커밋한다**

```bash
git add .claude/skills/review-code-against-docs/SKILL.md
git commit -m "feat: review-code-against-docs 스킬에 코드 구조 검사 추가"
```

Expected:
```
[feature/google XXXXXXX] feat: review-code-against-docs 스킬에 코드 구조 검사 추가
 1 file changed, N insertions(+), N deletions(-)
```
