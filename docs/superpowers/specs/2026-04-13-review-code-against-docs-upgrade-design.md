# review-code-against-docs 스킬 업그레이드 설계

**날짜**: 2026-04-13  
**대상 파일**: `.claude/skills/review-code-against-docs/SKILL.md`

---

## 개요

기존 `review-code-against-docs` 스킬에 **코드 구조 검사** 단계를 추가한다.  
기존 Step 0~3 구조를 유지하되, Step 1으로 코드 구조 검사를 삽입하고 기존 단계를 뒤로 밀어낸다.

---

## 변경 후 전체 구조

```
Step 0. diff 수집
Step 1. 코드 구조 검사     ← 신규
Step 2. 컨벤션 검사        ← 기존 (번호 변경 없음, 보고서 섹션만 2-1/2-2로 세분화)
Step 3. 스펙 정합성 검사   ← 기존
Step 4. ERD 정합성 검사    ← 기존
```

---

## Step 1. 코드 구조 검사 (신규)

### 1-1. 패키지 경로 검사

변경된 `.java` 파일의 경로가 CONTRIBUTING.md에 정의된 패키지 구조를 따르는지 확인한다.

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

### 1-2. 레이어 의존성 방향 검사

변경된 파일의 `import` 문을 분석해 다음 역방향 참조 패턴을 탐지한다.

| 위반 패턴 | 설명 |
|---|---|
| `controller/` → `repository/` | Controller가 Repository를 직접 import |
| `service/` → `controller/` (dto 포함) | Service가 Controller 또는 Controller DTO를 import |
| `repository/` → `service/` 또는 `controller/` | Repository가 상위 레이어를 import |

**허용**: `common/` 하위 클래스는 어느 레이어에서 참조해도 위반으로 보지 않는다.

---

## 보고서 포맷 변경

### 변경 사항

- 코드 구조 섹션(`### 1. 코드 구조`) 추가 — 보고서 최상단
- 컨벤션 섹션을 `### 2-1. 컨벤션 (프로덕션)`과 `### 2-2. 컨벤션 (테스트)`로 세분화

### 보고서 출력 예시

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
- [위반] PlaceControllerTest: @DisplayName 누락
- [통과] 중복 제거

### 3. 스펙 정합성
- [통과] 장소 검색 API — features.md 스펙과 일치

### 4. ERD 정합성
- [건너뜀] 엔티티 변경 없음

N개 이슈 발견
```

---

## 변경하지 않는 것

- Step 0 (`git diff main --name-only`) — 그대로 유지
- 태그 체계 (`[통과]` `[위반]` `[주의]` `[건너뜀]`) — 그대로 유지
- After the Report 정책 (자동 수정 없음) — 그대로 유지
- Step 3, 4 로직 — 그대로 유지
