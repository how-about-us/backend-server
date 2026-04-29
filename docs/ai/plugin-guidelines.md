# AI 플러그인 가이드라인

> Claude Code 플러그인(gstack, superpowers 등)이 문서를 생성할 때의 저장 위치와 규칙을 안내한다.

## superpowers

superpowers 스킬이 생성하는 문서는 `docs/superpowers/` 하위에 저장한다.

| 문서 유형 | 저장 경로 | 생성 스킬 |
|-----------|-----------|-----------|
| 기획 디자인 스펙 | `docs/superpowers/specs/` | brainstorming |
| 구현 계획 | `docs/superpowers/plans/` | writing-plans |

## gstack

gstack 스킬이 생성하는 문서는 `docs/gstack/` 하위에 저장한다.

| 문서 유형 | 저장 경로 | 생성 스킬 |
|-----------|-----------|-----------|
| 제품 기획 개요 | `docs/gstack/overview.md` | office-hours |
| 기획 디자인 스펙 | `docs/gstack/specs/` | office-hours |
| 기획 단계 계획 | `docs/gstack/plans/` | office-hours |

### gstack 추가 규칙

- 세션 시작 시 `docs/gstack/overview.md`를 먼저 읽고 제품 맥락을 파악한다.
- 세션 종료 시 `docs/gstack/overview.md`의 세션 이력 테이블을 갱신한다.

## 프로젝트 모드
모드 설정시에 아래의 기준을 따르며, 다시 설정에 대해 묻지 않는다.
- office-hours 스킬 실행 시 **Startup mode**로 진행한다.
- 제품 단계: **Pre-product** (아이디어/개발 단계, 실사용자 없음)

## 공통
- 문서 갱신 규칙은 `AGENTS.md`의 **Doc Update Rules** 섹션을 따른다.
- 플러그인 간 문서는 상대 경로로 상호 참조할 수 있다 (예: superpowers plan → gstack spec).
- 구현 계획 문서가 500줄을 초과하면 메인 plan 파일에는 Task 목록과 링크만 남기고, 각 Task의 세부 내용은 `tasks/` 하위 파일로 분리한다 (예: `plans/2026-04-29-kick-leave/tasks/task-01-error-code.md`).
