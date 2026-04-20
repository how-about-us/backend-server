# Schedule Management Design

## 목표

여행 방에서 일자별 일정과 일정 항목을 관리하는 1차 기능을 추가한다. 이번 단계에서는 일정 일자 생성, 목록 조회, 삭제와 일정 항목 생성, 목록 조회, 삭제, 시간 수정까지 제공한다.

## 배경

현재 문서에는 일정 도메인이 `schedules`와 `schedule_items`로 구분되어 있고, 여행 계획의 핵심 협업 대상이 채팅과 함께 일정으로 정의되어 있다. 다만 일정 항목의 순서 변경, 이동 정보 계산, WebSocket 브로드캐스트까지 한 번에 구현하면 외부 API와 비동기 처리까지 동시에 열려 범위가 커진다.

따라서 1차 구현은 기본 CRUD와 시간 설정까지만 닫고, 드래그 앤 드롭 재정렬과 Routes API 기반 이동 정보 갱신은 다음 단계로 분리한다.

## 범위

### 포함

- `Schedule` 엔티티, 리포지토리, 서비스, 컨트롤러 추가
- `ScheduleItem` 엔티티, 리포지토리, 서비스, 컨트롤러 추가
- 일정 일자 생성 API
- 일정 일자 목록 조회 API
- 일정 일자 삭제 API
- 일정 항목 생성 API
- 일정 항목 목록 조회 API
- 일정 항목 삭제 API
- 일정 항목 시간 수정 API (`startTime`, `durationMinutes`)
- 관련 테스트 및 문서 갱신

### 제외

- 일정 항목 메모(`memo`)
- 일정 항목 순서 변경
- 이동 거리/시간 계산
- 이동 수단 변경
- WebSocket 브로드캐스트
- 방 멤버십 인가
- 인증 사용자 기반 `createdBy`, `addedBy` 기록

## 도메인 구조

### Schedule

- `Room`을 `@ManyToOne(fetch = LAZY)`로 참조한다.
- 필드는 `dayNumber`, `date`, `createdAt`, `updatedAt`를 가진다.
- 한 방 안에서는 같은 `dayNumber`와 같은 `date`가 각각 하나만 존재해야 한다.

### ScheduleItem

- `Schedule`을 `@ManyToOne(fetch = LAZY)`로 참조한다.
- 필드는 `googlePlaceId`, `startTime`, `durationMinutes`, `orderIndex`, `travelMode`, `distanceMeters`, `durationSeconds`, `createdAt`, `updatedAt`를 가진다.
- 1차 구현에서는 `memo`를 엔티티와 API에 포함하지 않는다.
- `travelMode`, `distanceMeters`, `durationSeconds`는 컬럼은 두되 1차 구현에서는 생성/수정하지 않는다.
- 항목 추가 시 `orderIndex`는 같은 일정의 마지막 순번 다음 값으로 서버가 부여한다.

## API 설계

### 1. 일정 일자 생성

- `POST /rooms/{roomId}/schedules`

요청 바디:

- `dayNumber`
- `date`

응답 바디:

- `scheduleId`
- `roomId`
- `dayNumber`
- `date`
- `createdAt`

규칙:

- `dayNumber`는 1 이상이어야 한다.
- `date`는 방의 여행 기간 안에 있어야 한다.
- `date`는 `room.startDate + (dayNumber - 1)`와 일치해야 한다.
- 같은 방 안에서 `dayNumber` 또는 `date`가 중복되면 `409`

### 2. 일정 일자 목록 조회

- `GET /rooms/{roomId}/schedules`

응답 항목:

- `scheduleId`
- `roomId`
- `dayNumber`
- `date`
- `createdAt`

정렬:

- `dayNumber` 오름차순

### 3. 일정 일자 삭제

- `DELETE /rooms/{roomId}/schedules/{scheduleId}`

동작:

- 해당 일정에 속한 일정 항목을 먼저 삭제한 뒤 일자를 삭제한다.

규칙:

- 다른 방 소속 `scheduleId`를 현재 방 경로에서 사용하면 `404`

### 4. 일정 항목 생성

- `POST /rooms/{roomId}/schedules/{scheduleId}/items`

요청 바디:

- `googlePlaceId`
- `startTime` optional
- `durationMinutes` optional

응답 바디:

- `itemId`
- `scheduleId`
- `googlePlaceId`
- `startTime`
- `durationMinutes`
- `orderIndex`
- `createdAt`

규칙:

- `scheduleId`는 반드시 현재 방 소속이어야 한다.
- `durationMinutes`가 주어지면 양수여야 한다.
- 같은 일정 안에서 같은 `googlePlaceId` 중복 허용 여부는 1차에서는 허용한다. 하루 안에 같은 장소를 여러 번 배치할 수 있기 때문이다.

### 5. 일정 항목 목록 조회

- `GET /rooms/{roomId}/schedules/{scheduleId}/items`

응답 항목:

- `itemId`
- `scheduleId`
- `googlePlaceId`
- `startTime`
- `durationMinutes`
- `orderIndex`
- `createdAt`

정렬:

- `orderIndex` 오름차순

### 6. 일정 항목 삭제

- `DELETE /rooms/{roomId}/schedules/{scheduleId}/items/{itemId}`

동작:

- 현재 일정에 속한 항목만 삭제한다.
- 삭제 후 남은 항목의 `orderIndex`는 빈 값을 허용하지 않고 0부터 다시 연속 정렬한다.

규칙:

- 다른 일정 또는 다른 방 소속 `itemId`를 현재 경로에서 사용하면 `404`

### 7. 일정 항목 시간 수정

- `PATCH /rooms/{roomId}/schedules/{scheduleId}/items/{itemId}`

요청 바디:

- `startTime` optional
- `durationMinutes` optional

응답 바디:

- `itemId`
- `scheduleId`
- `googlePlaceId`
- `startTime`
- `durationMinutes`
- `orderIndex`
- `createdAt`

규칙:

- `startTime`, `durationMinutes`는 부분 수정으로 받는다.
- `durationMinutes`가 주어지면 양수여야 한다.
- `null`로 명시하면 해당 값은 제거할 수 있다.
- `memo`는 이 API에서 다루지 않는다.

## 검증 및 예외 처리

- 존재하지 않는 `roomId`는 `404`
- 존재하지 않는 `scheduleId` 또는 현재 방에 속하지 않는 `scheduleId`는 `404`
- 존재하지 않는 `itemId` 또는 현재 일정에 속하지 않는 `itemId`는 `404`
- 일정 일자의 `dayNumber`, `date` 중복은 `409`
- 방의 여행 기간과 맞지 않는 `dayNumber`, `date` 조합은 `400`
- 요청 바디 형식 오류, blank `googlePlaceId`, 0 이하 `durationMinutes`는 `400`

추가가 필요한 대표 오류는 아래와 같다.

- `SCHEDULE_NOT_FOUND`
- `SCHEDULE_ALREADY_EXISTS`
- `SCHEDULE_DATE_MISMATCH`
- `SCHEDULE_ITEM_NOT_FOUND`

## 서비스 경계

- `ScheduleService`는 방 존재 여부 확인, 여행 날짜 정합성 검증, 일정 일자 CRUD를 담당한다.
- `ScheduleItemService`는 일정 소속 검증, `orderIndex` 부여, 시간 수정, 삭제 후 재정렬을 담당한다.
- 두 서비스 모두 `RoomRepository` 또는 `ScheduleRepository`를 직접 사용해 현재 경로의 소속 관계를 검증한다.
- 1차 범위에서는 상위 파사드 서비스를 두지 않는다.

## 삭제 및 정렬 정책

- 일정 일자 삭제 시 일정 항목 삭제는 서비스에서 명시적으로 처리한다.
- 일정 항목 삭제 후 남은 항목의 `orderIndex`는 연속 값으로 다시 맞춘다.
- 항목 생성 시 `max(orderIndex) + 1` 규칙으로 마지막에 추가한다.
- 1차 구현에서는 동시 수정 충돌 제어나 gap 전략을 도입하지 않는다.

## 테스트 전략

### 서비스 테스트

- 여행 시작일과 `dayNumber`, `date` 조합이 일치하면 일정 생성 성공
- 여행 기간 밖 날짜로 일정 생성 시 실패
- `dayNumber`, `date` 불일치 시 실패
- 같은 방에서 `dayNumber` 중복 생성 시 실패
- 같은 방에서 `date` 중복 생성 시 실패
- 일정 항목 생성 시 마지막 `orderIndex + 1`이 부여됨
- 일정 항목 시간 부분 수정 성공
- 일정 항목 삭제 후 남은 항목 `orderIndex` 재정렬
- 다른 방 소속 일정 또는 다른 일정 소속 항목 접근 시 `404`

### 컨트롤러 테스트

- 일정 생성 request validation
- 일정 항목 생성 request validation
- 일정 항목 시간 수정 request validation
- 상태 코드 및 핵심 응답 필드 검증

### 통합 테스트

- 방 생성 후 일정 일자 생성 -> 목록 조회 -> 삭제
- 일정 일자 생성 후 항목 생성 -> 목록 조회 -> 시간 수정 -> 삭제
- 다른 방의 일정에 현재 방 경로로 접근할 수 없음
- 항목 삭제 후 남은 순서가 연속적으로 재배치됨

## 문서 반영

구현 시 함께 갱신해야 하는 문서:

- `docs/ai/features.md`
- `docs/ai/erd.md`

반영 내용:

- 일정 1차 범위를 `일자 CRUD + 항목 기본 CRUD + 시간 수정`으로 명확히 정리
- 1차 범위에서 `memo` 제외 상태를 문서에 반영
- 일정 항목 삭제 후 `orderIndex`를 연속 재배치하는 정책 명시

## 결정 요약

- 1차 일정 관리는 일자 CRUD와 항목 기본 CRUD, 시간 수정까지만 구현한다.
- `memo`, 이동 정보, 순서 변경, WebSocket은 다음 단계로 미룬다.
- 일정 생성은 `dayNumber`와 `date`를 둘 다 받고, 여행 시작일 기준으로 정합성을 검증한다.
- 일정 항목은 `googlePlaceId`를 직접 입력받는다.
- 항목 추가는 항상 마지막 순서에 붙이고, 삭제 후에는 연속 순번으로 재정렬한다.
