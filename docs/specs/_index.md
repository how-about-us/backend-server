# Specs Index

이 디렉토리는 프로젝트의 모든 기능/인프라 명세서(spec)를 관리합니다.

- **진실의 원천(Source of Truth)**: 이 디렉토리의 각 spec 파일
- **Agent 참조**: Claude/Codex가 기능 구현 시 해당 spec 파일을 먼저 읽습니다
- **Notion 동기화**: spec 파일의 상태 변경은 Notion 뷰어와 단방향(Local → Notion)으로 동기화됩니다

## 업데이트 규칙

> spec이 새로 추가되거나 상태가 변경되면, 이 `_index.md`를 **반드시 함께 갱신**해야 합니다.
> 완료(`완료`) 상태로 전환된 spec은 아래 "완료된 Spec" 섹션으로 이동합니다.

---

## ID 체계

| Prefix | 용도 |
|--------|------|
| `FE-XXX` | Feature — 사용자 기능 |
| `INFRA-XXX` | Infrastructure — 하네스, 배포, CI/CD 등 |
| `REFACTOR-XXX` | Refactor — 기존 코드 구조 개선 |

---

## 진행중 / 대기

| ID | 제목 | 상태 | 담당 | 우선순위 |
|----|------|------|------|----------|
| [INFRA-001](INFRA/INFRA-001-하네스-리팩토링.md) | 하네스 리팩토링 | 진행중 | 박주영 | P1 |
| [INFRA-002](INFRA/INFRA-002-역할별-skills-구축.md) | 역할별 Skills 구축 | 진행중 | 박주영 | P2 |

## 완료된 Spec

| ID | 제목 | 완료일 | 담당 |
|----|------|--------|------|
| _(없음)_ | | | |

---

## 새 Spec 작성 방법

1. ID 체계에 따라 prefix를 선택한다 (`FE`, `INFRA`, `REFACTOR` 등)
2. 해당 prefix 디렉토리 아래에 `{ID}-{kebab-case-title}.md` 파일을 생성한다
3. spec 파일 템플릿(`_TEMPLATE.md`, 추후 생성 예정)을 복사해 내용을 채운다
4. 이 `_index.md`의 "진행중 / 대기" 표에 행을 추가한다
5. PR을 올려 리뷰를 받는다

## 상태값

- `대기` — 작성은 됐지만 아직 착수 전
- `진행중` — 현재 구현/작업 중
- `완료` — DoD 충족, "완료된 Spec" 섹션으로 이동
- `보류` — 의존성/결정 대기로 일시 중단
