# 기능 명세서

> ERD 초안 기준으로 정리한 기능 목록입니다. 각 기능은 API 명세서 작성의 단위가 됩니다.

## 프로젝트 개요

다같이 여행 계획을 세우는 협업 웹 서비스.

- **화면 구성**: 좌측 지도 / 우측 채팅·일정·보관함
- **핵심 기능**: AI 에이전트가 채팅방 팀원으로 참여해 여행 계획 수립 보조

---

## 구현 상태 범례

| 상태 | 의미 |
|------|------|
| `[ ]` | 미구현 |
| `[x]` | 구현 완료 |
| `[-]` | 보류 / MVP 이후 |

---

## 1. 인증 (Auth)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 구글 OAuth 로그인 | Google 계정으로 소셜 로그인 | users |
| `[ ]` | 토큰 재발급 (Refresh) | Access Token 만료 시 갱신 | Redis |
| `[ ]` | 로그아웃 | 토큰 무효화 | Redis |
| `[ ]` | 내 정보 조회 | 로그인된 사용자 프로필 조회 | users |

---

## 2. 여행 방 (Rooms)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 방 생성 | 방 제목, 여행지, 날짜 입력 → invite_code 자동 발급 | rooms |
| `[ ]` | 내 방 목록 조회 | 내가 참여 중인 방 목록 | rooms, room_members |
| `[ ]` | 방 상세 조회 | 방 메타정보 (제목, 여행지, 날짜, 멤버 수 등) | rooms |
| `[ ]` | 방 수정 | 방 제목, 여행지, 날짜 수정 (ADMIN만) | rooms |
| `[ ]` | 방 삭제 | 방 삭제 (ADMIN만) | rooms |
| `[ ]` | 초대 코드 재발급 | 기존 invite_code 폐기 후 신규 발급 (ADMIN만) | rooms |
| `[ ]` | 초대 링크로 방 입장 | invite_code로 room_members에 등록 | rooms, room_members |

---

## 3. 멤버 관리 (Room Members)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 방 멤버 목록 조회 | 방 참여자 목록 + 역할(ADMIN/MEMBER) + 접속 상태 | room_members |
| `[ ]` | 멤버 강퇴 | ADMIN이 특정 멤버 내보내기 | room_members |
| `[ ]` | 방 나가기 | 본인이 방에서 탈퇴 | room_members |
| `[ ]` | 현재 접속 중인 유저 조회 | 실시간 접속 유저 목록 | Redis (connected_users) |
| `[-]` | 방장 위임 | 권한 이전 | room_members |

---

## 4. 장소 (Places)

> `places` 테이블 없이 `google_place_id`를 직접 사용한다. 검색은 캐시하지 않고, 장소 상세 조회 payload는 Redis에 10분 TTL로 캐시한다.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[x]` | 장소 검색 | Google Places API (New)로 장소 검색 | - |
| `[ ]` | 장소 상세 조회 | 장소명, 주소, 평점, 사진 등, 상세 조회 결과는 Redis에 10분 TTL 캐시 | Redis |

---

## 5. 보관함 (Bookmarks)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 보관함에 장소 추가 | 검색된 장소를 방의 후보지로 등록 | bookmarks |
| `[ ]` | 보관함 목록 조회 | 방의 후보지 목록 (카테고리 필터) | bookmarks |
| `[ ]` | 보관함 항목 삭제 | 후보지에서 제거 | bookmarks |
| `[ ]` | 보관함 메모 수정 | 항목별 메모 편집 | bookmarks |
| `[ ]` | 보관함 카테고리 변경 | 항목 카테고리 수정 | bookmarks |
| `[ ]` | 장소 채팅에 공유 | 보관함/검색 장소를 채팅 메시지로 전송 | bookmarks → messages |

---

## 6. 일정 (Schedules)

### 6-1. 일정 (Schedules — 일자 단위)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 일정 생성 | N일차 + 날짜 등록 | schedules |
| `[ ]` | 일정 목록 조회 | 방의 전체 일자별 일정 조회 | schedules |
| `[ ]` | 일정 삭제 | 특정 일자 삭제 (하위 items 포함) | schedules |

### 6-2. 일정 항목 (Schedule Items — 장소 단위)

> **이동 정보 갱신 흐름:** 순서 변경(HTTP) → WS 브로드캐스트 → @Async Routes API 호출 → DB 저장 → WS 브로드캐스트

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 일정에 장소 추가 | 특정 일자에 장소 추가 (보관함 또는 검색에서 바로) | schedule_items |
| `[ ]` | 일정 항목 목록 조회 | 특정 일자의 장소 목록 (order_index 순) | schedule_items |
| `[ ]` | 일정 항목 삭제 | 일자에서 장소 제거 | schedule_items |
| `[ ]` | 일정 순서 변경 (D&D) | order_index 재정렬 → WebSocket 브로드캐스트 | schedule_items |
| `[ ]` | 일정 항목 메모 수정 | 방문 메모 편집 | schedule_items |
| `[ ]` | 시간 설정 | start_time, duration_minutes 설정 | schedule_items |
| `[ ]` | 이동 정보 조회 | 비동기 계산된 distance_meters, duration_seconds, travel_mode 조회 | schedule_items |
| `[ ]` | 이동 수단 변경 | travel_mode 수동 변경 → 비동기로 Routes API 재호출 | schedule_items |

---

## 7. 채팅 (Messages)

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | 메시지 전송 (WS) | 실시간 텍스트 채팅 | messages |
| `[ ]` | 메시지 목록 조회 | 방 채팅 히스토리 (페이지네이션) | messages |
| `[ ]` | 재접속 시 미수신 메시지 동기화 | 마지막 수신 message.id 이후 메시지 조회 | messages |
| `[ ]` | 장소 카드 메시지 전송 | place 정보를 채팅에 공유 (message_type: PLACE_SHARE) | messages |
| `[ ]` | 시스템 메시지 | 멤버 입퇴장 등 시스템 이벤트 알림 (message_type: SYSTEM) | messages |

---

## 8. AI 기능

> **미결:** AI 응답 이력을 messages 테이블로 관리할지 별도 테이블로 분리할지, 어떤 컨텍스트(방 일정, 보관함, 위치 등)를 전달할지 결정 필요.

| 상태 | 기능 | 설명 | ERD 연관 |
|------|------|------|----------|
| `[ ]` | AI 호출 (룰 기반 트리거) | 채팅창에서 AI 호출 버튼으로 질의 | messages (message_type: AI_RESPONSE) |
| `[ ]` | AI 응답 채팅에 표시 | AI 응답을 sender_id=NULL 메시지로 채팅에 노출 | messages |

---

## 미결 사항

| # | 항목 | 선택지 | 현황 |
|---|------|--------|------|
| 1 | AI 응답 저장 방식 | messages.message_type vs ai_responses 별도 테이블 | 미결 |
| 2 | schedules 기준값 | day_number, date 두 컬럼 모두 저장 | 결정 |
| 3 | schedule_items order_index 중복 방지 | UNIQUE 제약 vs gap 전략 | 미결 |
| 4 | room_members 직접 참조 정합성 | sender_id, added_by → users 직접 vs room_members 참조 | 미결 |
| 5 | 방장 위임 기능 | MVP 이후 진행 | 보류 |
| 6 | 방 삭제 정책 | soft delete vs hard delete + CASCADE | 미결 |
| 7 | 초대 링크 만료/횟수 제한 | room_invitations 테이블 분리 시점 기준 | 미결 |
