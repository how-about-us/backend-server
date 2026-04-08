# 하네스 엔지니어링 설계

## 진행 방향

**Harness + SDD** 조합으로 전체 Agent 작업 환경을 구성하고 제어한다.

- **Harness**: AGENTS.md / CLAUDE.md / settings.json / skills / hooks로 Agent가 작업하는 규칙·환경 전체를 정의한다.
- **SDD (Spec Driven Develop)**: 기능명세서를 진실의 원천(Source of Truth)으로 두고, Agent가 spec을 읽고 구현한다.
- **Hook**: settings.json에 hook을 설정해 특정 시점(편집 후, 커밋 전 등)에 lint·build·배포 자동화를 실행한다.

---

## 현재 하네스 구조 (26.04.08 기준)

```
backend-server/
├── AGENTS.md          ← 공통 하네스 진입점 (Claude + Codex 공용)
├── CLAUDE.md          ← Claude 전용 오버레이
└── docs/ai/
    ├── features.md    ← 기능 명세 (구현 상태 추적)
    ├── api-spec.md    ← API/WS 명세
    ├── erd.md         ← DB 스키마
    └── decisions/
        ├── README.md  ← ADR 작성 가이드
        └── TEMPLATE.md
```

### 지침 파일 역할 분리

| 파일 | 역할 | 대상 |
|------|------|------|
| `AGENTS.md` | 공통 컨텍스트 — 기술 스택, 명령어, 프로파일, Gotchas, 문서 인덱스 | Claude + Codex |
| `CLAUDE.md` | Claude 전용 오버레이 — AGENTS.md를 위임하고 Claude에게만 필요한 규칙 추가 | Claude Code |

> AGENTS.md가 공통 Source of Truth다. CLAUDE.md는 AGENTS.md를 @import하고, Claude에게만 필요한 규칙이 생길 때만 추가한다.

### 파일 우선순위
중복 설정이 있을 경우, 우선순위가 높은 파일이 낮은 파일을 덮어쓴다.
```
전역 ~/.claude/CLAUDE.md
  └─ 프로젝트 CLAUDE.md   (Claude 전용, AGENTS.md 위임)
       └─ AGENTS.md       (공통 기준)
```

---
## AGENTS.md 작성 원칙

AGENTS.md에 포함할 내용:

- **Before You Start**: 영향 범위 식별(기능/API/DB) → 필요한 문서만 골라 읽기
- **Tech Stack**: 사용 중인 프레임워크, DB, 캐시, 빌드 도구
- **Commands**: `bootRun`, `build`, `test`, Docker Compose 제어
- **Profiles**: `dev` / `prod` 환경 정의
- **Gotchas**: 실수하기 쉬운 설정 (PostGIS 이미지, `open-in-view=false`, WebSocket Security 등)
- **Project Docs**: 어떤 상황에서 어떤 문서를 읽어야 하는지
- **Doc Update Rules**: 코드 변경 → 대응 문서 함께 갱신 의무

> 지침 파일이 너무 길면 컨텍스트 윈도우를 낭비한다. **300줄 이내**를 유지한다. 내용이 길어지면 `docs/ai/` 아래 별도 파일로 분리하고, AGENTS.md에는 "언제 어떤 문서를 읽어야 하는지"만 남긴다.

---

## CLAUDE.md 작성 방법

### 역할 부여

페르소나를 명시하면 일관된 판단 기준을 갖게 된다.

```markdown
## 역할
너는 대규모 트래픽을 다뤄본 시니어 백엔드 엔지니어다.
- 모든 API 응답에 적절한 HTTP 상태 코드를 사용한다
- 에러 핸들링은 미들웨어 레벨에서 일괄 처리한다
- N+1 쿼리 문제를 항상 경계한다
- 새 엔드포인트에는 rate limiting을 고려한다
```

### 강한 금지 (이유 포함)

이유 없이 금지만 나열하면 우회하거나 엉뚱한 선택을 하기도 한다. **금지 + 이유**를 함께 작성한다.

```markdown
## 하지 말 것
- any 타입 금지 — TypeScript strict mode를 사용하므로 타입 안전성을 보장해야 한다
- console.log 금지 — 프로덕션 로깅은 winston logger를 통해 구조화된 형태로 남긴다
```

### 경로별 규칙

```markdown
## 경로별 규칙
- `src/components/ui/` 아래 파일은 수정하지 말 것 (shadcn/ui 원본 유지)
- `src/app/api/` 아래 라우트 핸들러는 반드시 try-catch로 감쌀 것
```

### 작업 유형별 규칙

```markdown
## 작업별 규칙
### 테스트 코드 작성 시
- describe/it 패턴을 사용한다
- mock은 최소한으로 사용하고, 가능하면 실제 구현을 테스트한다

### 리팩토링 시
- 동작 변경 없이 구조만 개선한다
- 한 번에 하나의 리팩토링만 수행한다
```

### 블랙리스트보다 화이트리스트

모든 권한을 주고 위험한 것을 빼는 방식보다, 필요한 것만 여는 방식이 안전하다.

```markdown
## 허용된 작업
- src/, tests/ 디렉토리 내 파일 수정
- pnpm test, pnpm lint, pnpm build 실행
- git status, git diff, git log 조회
- 위 목록에 없는 작업은 사용자에게 확인을 요청한다
```

### 실패 안전 설계

모호한 상황에서 스스로 판단하지 않도록 설계한다.

```markdown
## 모호한 상황 처리
- 요구사항이 불명확하면 구현하지 말고 질문한다
- 기존 코드의 의도를 파악할 수 없으면 임의로 수정하지 않는다
- 두 가지 이상의 구현 방식이 가능하면 선택지를 제시하고 결정을 요청한다
- 파괴적 변경(breaking change)이 예상되면 반드시 사전에 알린다
```

### Hook 설정 (피드백 루프)

Claude가 코드를 수정하면 → 자동으로 lint 검사 → 결과를 Claude에게 전달 → Claude가 스스로 수정하는 사이클을 만든다.

```bash
#!/bin/bash
# .claude/hooks/post-edit-lint.sh
# PostToolUse(Edit) hook

FILE="$TOOL_INPUT_PATH"
RESULT=$(npx eslint "$FILE" --format compact 2>&1)

if [ $? -ne 0 ]; then
  echo "린트 오류 발견 - 아래 문제를 수정해주세요:"
  echo "$RESULT"
  exit 0  # exit 0으로 작업을 차단하지 않되, stdout으로 피드백 전달
fi

echo "린트 통과"
```

commit, build 등도 동일한 방식으로 자동화 가능하다.

---

## SDD (Spec-Driven Development)

**핵심 아이디어: Local Spec → Git → Notion (단방향 흐름)**

Notion → Local 동기화가 불안정하므로, **진실의 원천을 로컬 spec 파일**로 두고 Notion은 팀 공유용 뷰어로 사용한다.

### 폴더 구조

```
docs/
├── specs/                    ← 기능명세서 (Notion 동기화 대상)
│   ├── _index.md             ← 전체 기능 목록 + 상태 요약
│   ├── FE-001-실시간-채팅.md
│   ├── FE-002-위치-공유.md
│   └── ...
└── ai/                       ← Agent 전용 기술 문서
    ├── api-spec.md
    ├── erd.md
    └── decisions/
```

각 spec 파일에 고정 ID(FE-001 등)를 부여하면 Git diff로 변경 추적이 가능하다. Notion 인라인 데이터베이스에 ID를 1:1 매핑해두면 MCP 연동 시 동기화가 쉬워진다.

### Spec 파일 템플릿

Agent가 파싱하기 쉽도록 구조를 고정한다.

```markdown
# FE-001: 실시간 채팅

## 메타
- 상태: 개발중 | 완료 | 대기
- 담당: backend/박주영
- 우선순위: P1
- Notion ID: xxx

## 개요
한 줄 설명

## 기능 요구사항
- [ ] 1:1 채팅 메시지 송수신
- [ ] 읽음 표시
- [ ] 이미지 첨부

## API 연동
- POST /api/chat/messages → api-spec.md#chat-send 참조
- WS /ws/chat → api-spec.md#chat-ws 참조

## DB 변경
- messages 테이블 추가 → erd.md#messages 참조

## 제약사항 / 결정사항
- WebSocket은 STOMP 프로토콜 사용 (ADR-003 참조)
```

### SDD 워크플로우

AGENTS.md에 다음 섹션을 추가하면 Agent가 SDD 흐름을 따른다.

```markdown
## 기능 개발 워크플로우 (SDD)
1. docs/specs/_index.md에서 대상 기능의 spec 파일 경로를 확인한다
2. 해당 spec 파일을 읽고, 요구사항/API/DB 변경사항을 파악한다
3. 구현 전 영향 범위를 확인한다 (api-spec.md, erd.md 참조)
4. 구현 완료 후 spec 파일의 체크박스를 갱신한다
5. spec에 없는 기능을 임의로 추가하지 않는다
```

### Notion 동기화

두 가지 방법 중 선택한다.

**수동**: spec PR 머지 후 담당자가 Notion에 직접 반영. Git diff 기준으로 변경된 부분만 업데이트.
