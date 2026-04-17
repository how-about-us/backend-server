---
name: review-code-against-docs
description: Use when the user asks to create, open, prepare, or publish a PR/pull request, says PR 생성 or PR 올리기, or wants to verify that changed code matches docs/specs before review
---

# Code-Docs Review

## When to Use

사용자가 PR 생성/오픈/준비를 요청했거나, "내 구현이 스펙대로 맞는지" 리뷰 전에 확인해야 할 때 실행한다.

## Checklist

### Step 0. diff 수집

```bash
git diff main --name-only
```

변경된 `.java` 파일 목록을 추출한다. 변경 파일이 없으면 "변경 없음"으로 종료한다.

변경된 파일을 두 그룹으로 분류한다:
- **프로덕션 코드**: `src/main/` 하위 파일
- **테스트 코드**: `src/test/` 하위 파일

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

### Step 2. 컨벤션 검사

1. `CONTRIBUTING.md`를 읽는다.
2. `Code Convention` 섹션 전체(하위 섹션 `테스트` 포함)에서 규칙을 추출한다.
3. 변경 파일별로 해당 규칙을 적용한다:
   - `src/main/` 파일 → 일반 코드 규칙 적용
   - `src/test/` 파일 → 테스트 규칙 적용
4. 각 규칙에 대해 위반 여부를 판단하고 결과를 기록한다.

스킬이 "어떤 규칙이 있는지"를 미리 알 필요가 없다. `CONTRIBUTING.md`에서 읽은 내용이 검사 기준의 전부다.

### Step 3. 스펙 정합성 검사

1. `docs/ai/features.md`를 읽는다.
2. 변경된 파일의 패키지명으로 도메인을 추론하고, 관련 기능 섹션을 찾는다.
3. 해당 기능의 설명과 실제 구현 내용을 대조한다.
4. 구현이 완료됐는데 `[ ]`로 남아 있는 항목은 `[주의]`로 표시한다.

### Step 4. ERD 정합성 검사

`entity/` 경로 하위 파일이 변경된 경우에만 실행한다. 없으면 `[건너뜀]`으로 표시하고 Step 4를 종료한다.

1. `docs/ai/erd.md`를 읽는다.
2. 변경된 엔티티와 대응하는 테이블 정의를 찾는다.
3. 컬럼명, 타입, 제약조건을 `@Column`, 필드명, JPA 어노테이션과 대조한다.

## Report Format

태그는 네 가지로 통일한다.

| 태그 | 의미 |
|------|------|
| `[통과]` | 문제 없음 |
| `[위반]` | 컨벤션 또는 스펙 불일치 발견 |
| `[주의]` | 명확하지 않아 사람이 직접 확인 필요 |
| `[건너뜀]` | 해당 파일 없어 검사 생략 |

보고서 출력 예시:

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

위반 보고 시 해당 규칙이 어느 문서의 어느 문장에서 왔는지 함께 인용한다.

## After the Report

이슈가 발견된 경우:
- 보고서를 출력하고 사용자가 수정 여부를 판단하도록 한다.
- 자동 수정하지 않는다.

이슈가 없는 경우:
- "이슈 없음"으로 명확히 종료한다.
