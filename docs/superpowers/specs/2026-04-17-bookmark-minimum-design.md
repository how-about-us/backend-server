# Bookmark Minimum Design

## 목표

보관함 기능의 1차 수직 슬라이스를 구현한다. 이번 단계에서는 인증과 방 멤버십 인가를 붙이지 않고, `Room` 최소 엔티티를 기준으로 북마크 `추가 / 목록 조회 / 삭제`만 제공한다.

## 범위

### 포함

- `Room` 최소 엔티티 및 리포지토리 추가
- `Bookmark` 엔티티, 리포지토리, 서비스, 컨트롤러 추가
- 북마크 추가 API
- 북마크 목록 조회 API
- 북마크 삭제 API
- 요청 검증, 중복 처리, 방 존재 여부 검증

### 제외

- 방 생성, 수정, 삭제 API
- `room_members` 기반 멤버십 검증
- JWT 기반 요청 사용자 식별
- `addedBy` 실제 사용자 기록
- 북마크 메모 기능
- 북마크 카테고리 필터 조회
- 장소 상세 조회와의 직접 연동

## 배경

현재 저장소에는 장소 검색 및 상세 조회, Google OAuth 로그인, Refresh Token 발급 기능은 있으나, 방과 북마크 도메인은 아직 없다. ERD에서는 `bookmarks.room_id`가 `rooms.id`를 참조하도록 정의되어 있으므로, 북마크만 단독으로 UUID 값을 저장하는 구조보다 `Room` 최소 엔티티를 먼저 두는 편이 문서와 구현의 정합성이 높다.

또한 팀 결정에 따라 북마크 메모 기능은 제외한다. 따라서 북마크 1차 설계는 `googlePlaceId`, `category`, `addedBy` 중심으로만 구성한다.

## 설계

### 1. 도메인 구조

#### Room

- 최소 엔티티로만 도입한다.
- 이번 단계에서 필요한 책임은 "북마크가 참조할 수 있는 방 식별자 보유" 뿐이다.
- 엔티티 필드는 ERD의 `rooms` 컬럼을 기준으로 맞춘다. 즉, API는 만들지 않더라도 `id`, `title`, `destination`, `startDate`, `endDate`, `inviteCode`, `createdBy`는 모델에 포함한다.
- 방 관련 API는 이번 범위에서 만들지 않는다.

#### Bookmark

- `Room`을 `@ManyToOne(fetch = LAZY)`로 참조한다.
- `googlePlaceId`는 문자열 컬럼으로 저장한다.
- `category`는 문자열 컬럼으로 저장하고 기본값은 `"ALL"`로 둔다.
- `addedBy`는 인증 미연동 상태를 고려해 1차에서는 nullable로 둔다. 이는 현재 ERD의 `NOT NULL`과 일시적으로 다르며, 인증 연동 단계에서 다시 `NOT NULL` 제약으로 맞춘다.
- 방 안에서는 동일한 `googlePlaceId`를 한 번만 저장할 수 있어야 한다.

### 2. API 구조

방 하위 리소스 경로로 북마크를 둔다.

- `POST /rooms/{roomId}/bookmarks`
- `GET /rooms/{roomId}/bookmarks`
- `DELETE /rooms/{roomId}/bookmarks/{bookmarkId}`

이 구조는 이후 인증과 멤버십 검증이 추가되어도 경로를 바꿀 필요가 없고, 리소스 소속 관계가 분명하다.

### 3. 요청 / 응답 계약

#### 북마크 추가

요청 바디:

- `googlePlaceId`: 필수
- `category`: 선택, 누락 시 `"ALL"`

응답 바디:

- `bookmarkId`
- `roomId`
- `googlePlaceId`
- `category`
- `addedBy`
- `createdAt`

#### 북마크 목록 조회

- 특정 방의 북마크 전체 목록을 생성일시 역순으로 반환한다.
- 1차에서는 카테고리 필터를 제공하지 않는다.

응답 항목:

- `bookmarkId`
- `roomId`
- `googlePlaceId`
- `category`
- `addedBy`
- `createdAt`

#### 북마크 삭제

- 경로의 `roomId`에 속한 `bookmarkId`만 삭제할 수 있다.
- 성공 시 `204 No Content`

### 4. 검증 및 예외 처리

- 존재하지 않는 `roomId`로 요청하면 `404`
- 존재하지 않는 `bookmarkId`를 삭제하면 `404`
- 다른 방에 속한 `bookmarkId`를 현재 `roomId` 경로로 삭제해도 `404`
- 같은 방에 같은 `googlePlaceId`를 중복 등록하면 `409`
- `googlePlaceId`가 blank면 `400`

1차에서는 인증이 열려 있으므로 `addedBy = null` 저장을 정상 케이스로 본다.

### 5. 데이터 모델 원칙

- `Room`이 실제 엔티티여야 `Bookmark.room`에 `@ManyToOne`을 둘 수 있다.
- 저장 시에는 `Room` 전체를 다시 저장하지 않고, 기존 `roomId`를 참조하는 엔티티 프록시 또는 조회 결과를 연결해 `Bookmark`만 저장한다.
- 실제 `rooms` 행이 없으면 북마크 생성 전에 서비스 계층에서 `404`로 차단한다.

### 6. 인증 연동 이후 확장 방향

이번 설계는 북마크 최소 기능 구현을 우선한다. 이후 인증 연동 시에는 아래만 추가하면 된다.

- JWT 검증 필터
- 현재 사용자 주입 방식 확정
- `addedBy`를 현재 로그인 사용자로 기록
- 북마크 생성/조회/삭제 시 방 참여자 검증

즉, 이번 설계는 이후 인가 로직을 덧붙일 수 있도록 엔티티 연관과 API 경로를 먼저 안정화하는 데 목적이 있다.

## 테스트 전략

### 컨트롤러 테스트

- `googlePlaceId` blank 검증
- 상태 코드 검증
- 서비스 호출 파라미터 검증

### 통합 테스트

- 존재하는 방에 북마크 추가
- 특정 방 북마크 목록 조회
- 특정 방 북마크 삭제
- 같은 방에서 동일 `googlePlaceId` 중복 등록 시 충돌
- 없는 방에 대한 추가 / 조회 / 삭제 실패

### 비포함 테스트

- Google Places API 연동 검증
- 인증 사용자 추출 검증
- 멤버십 인가 검증

## 문서 반영 원칙

- 북마크 메모는 팀 결정에 따라 제외한다.
- `docs/ai/features.md`와 `docs/ai/erd.md`는 이미 북마크 메모 제거 방향으로 정리되어야 한다.
- 구현 완료 후에는 북마크 관련 구현 상태를 최신 상태에 맞게 갱신한다.
