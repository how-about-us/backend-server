# Decisions Guide

`docs/ai/decisions/`는 ADR(Architecture Decision Record) 스타일로 중요한 설계 결정을 짧게 남기는 폴더다.

## When To Add A Record

- 미결 사항이 팀 합의로 확정되었을 때
- 여러 구현 방향 중 하나를 선택했고, 나중에 이유를 다시 봐야 할 때
- 문서나 코드만 봐서는 의도를 알기 어려운 구조적 선택을 했을 때

## File Naming

- 파일명 형식은 `YYYYMMDD-HHMM-short-title.md`를 사용한다.
- 예: `20260331-1430-message-delivery-via-stomp.md`
- 같은 날 여러 기록을 만들 때는 생성 시각까지 포함해 충돌을 피한다.
- 순차 번호는 사용하지 않는다.

## Recommended Sections

- `Status`
- `Context`
- `Decision`
- `Consequences`
- `Related Docs`

새 기록을 만들 때는 `TEMPLATE.md`를 복사해서 시작한다.
