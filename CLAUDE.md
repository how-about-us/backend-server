# how-about-us-backend

Claude 전용 메모 문서.

공통 프로젝트 안내는 `AGENTS.md`를 기준으로 관리한다.

## Shared Context

- 공통 기술 스택, 실행 명령어, 프로파일, 아키텍처, 주의사항은 `AGENTS.md`를 먼저 확인한다.

## Skills 사용 안내

작업 맥락에 맞는 skill을 선택적으로 로드한다. **Skill의 도메인 규칙이 CLAUDE.md의 일반 규칙보다 우선한다.**

| 작업 맥락 | Skill |
|-----------|-------|
| 코드 리뷰 (서비스/컨트롤러/리포지토리) | `senior-backend-review` |
| 기능 구현 (서비스/컨트롤러/리포지토리 작성) | `backend-implementer` |
| DB 스키마 설계 / 마이그레이션 작성 | `db-schema-designer` |
| REST API / WebSocket / STOMP 설계 | `api-designer` |
| 테스트 작성 (단위/통합/슬라이스) | `test-writer` |
| 버그/장애 원인 분석 | `debugger` |
| Notion spec 동기화 (_index.md → Notion) | `notion-sync` |

Skills는 `.claude/skills/` 디렉토리에 위치한다. 새 skill 추가 시 `_TEMPLATE/SKILL.md`를 참고한다.
