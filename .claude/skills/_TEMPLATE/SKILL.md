---
name: skill-name
description: >
  Use when [작업 맥락 설명]. Covers [핵심 역량 1], [핵심 역량 2], [핵심 역량 3].
  Make sure to use this skill whenever [트리거 키워드/상황].
# allowed-tools: Read, Edit, Write, Bash  # 필요한 경우에만 명시
---

# [Skill 이름]

## 역할 (Role)

[이 skill이 수행하는 페르소나를 한 문장으로 기술한다.]

예: "대규모 트래픽을 경험한 시니어 백엔드 엔지니어 관점에서 [작업]을 수행한다."

---

## 원칙 (Principles)

각 원칙에는 **이유**를 함께 기술한다. 이유 없는 규칙은 맥락이 바뀌면 잘못 적용된다.

1. **[원칙 이름]** — [이유]
2. **[원칙 이름]** — [이유]
3. **[원칙 이름]** — [이유]

---

## 작업 전 체크리스트

- [ ] [시작 전 확인 항목 1]
- [ ] [시작 전 확인 항목 2]
- [ ] [관련 문서 확인 — 예: `docs/ai/api-spec.md`]

## 작업 후 체크리스트

- [ ] [완료 기준 1]
- [ ] [관련 문서 갱신 의무 — 예: `docs/ai/erd.md` 동기화]
- [ ] [검증 방법 기술]

---

## 금지 사항 (Never Do)

금지 사항에도 **이유**를 붙인다. 이유가 명확해야 유사한 상황에서 올바른 판단을 내릴 수 있다.

| 금지 행동 | 이유 |
|---|---|
| [금지 행동 1] | [이유] |
| [금지 행동 2] | [이유] |

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| [문서명] | `docs/ai/[파일명].md` | [언제 읽어야 하는지] |

---

## 이 프로젝트 특이사항

how-about-us-backend 스택 고유 주의 항목:

- `open-in-view=false` — 트랜잭션 밖에서 Lazy 로딩 불가
- PostGIS `Point` 타입 — Java 내 거리 계산 금지, DB(`ST_DWithin`)에 위임
- Spring Boot 4 / Spring 6 — `jakarta.*` 패키지 사용
- WebSocket + STOMP — Security 설정에서 WebSocket 엔드포인트 별도 허용 필요
