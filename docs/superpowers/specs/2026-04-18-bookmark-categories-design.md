# Bookmark Categories Design

## 목표

방별 사용자 정의 북마크 카테고리 기능을 추가한다. 카테고리는 방마다 독립적으로 관리되고, 북마크는 반드시 하나의 카테고리에 속해야 한다.

## 배경

현재 북마크는 `category` 문자열 컬럼으로 분류를 표현한다. 이 구조는 서버가 허용값을 강제하지 않으면 오타, 중복 명칭, 대소문자 변형 같은 데이터 오염이 쉽게 생긴다. 또한 각 방마다 사용자가 원하는 카테고리 체계가 다를 수 있어 전역 enum으로 고정하기도 어렵다.

따라서 카테고리를 별도 테이블로 승격하고, 북마크는 카테고리 엔티티를 참조하도록 바꾼다.

## 범위

### 포함

- `bookmark_categories` 테이블 및 엔티티 추가
- 방별 카테고리 생성 API
- 방별 카테고리 목록 조회 API
- 카테고리 이름 변경 API
- 카테고리 삭제 API
- 북마크 생성 시 `categoryId` 필수화
- 북마크 카테고리 변경 API
- 카테고리 삭제 시 소속 북마크 함께 삭제
- 관련 테스트 및 문서 갱신

### 제외

- 카테고리 색상, 아이콘, 정렬 순서
- 카테고리 soft delete
- 카테고리별 북마크 개수 집계 필드
- 인증 사용자 기반 `createdBy`, `addedBy` 실제 기록
- 방 멤버십 인가

## 데이터 모델

### bookmark_categories

- `id`: BIGINT PK
- `room_id`: UUID, `rooms.id` 참조
- `name`: VARCHAR(50), NOT NULL
- `created_by`: BIGINT, 사용자 ID 참조, NULL 가능
- `created_at`, `updated_at`

제약:

- `UNIQUE(room_id, name)`

### bookmarks

- 기존 `category` 문자열 컬럼 제거
- `category_id` 추가
- `category_id`는 `NOT NULL`

의미 규칙:

- 방 생성 직후 카테고리는 0개일 수 있다.
- 북마크는 반드시 카테고리에 속해야 한다.
- 따라서 카테고리가 하나도 없는 방에서는 북마크 생성이 불가능하다.
- 클라이언트의 `ALL`은 저장값이 아니라 “필터 없음” 의미만 가진다.

## 연관관계와 삭제 정책

- `Bookmark`는 `BookmarkCategory`를 참조한다.
- 삭제 전파는 JPA cascade에 맡기지 않고 서비스에서 명시적으로 처리한다.
- 카테고리 삭제 시 순서는 아래와 같다.
  1. 해당 카테고리에 속한 북마크 삭제
  2. 카테고리 삭제

이 방식을 택하는 이유:

- 서비스 코드에서 “카테고리 삭제는 북마크 삭제를 동반한다”는 의도가 명확하다.
- 나중에 삭제 전 검증, 감사 로그, 다른 카테고리로 이동 같은 정책이 생겨도 서비스에서 제어하기 쉽다.
- 현재 프로젝트는 마이그레이션보다 Hibernate DDL 비중이 크므로, DB cascade보다 애플리케이션 코드에 정책을 드러내는 편이 안전하다.

## API 설계

### 1. 카테고리 생성

- `POST /rooms/{roomId}/bookmark-categories`

요청 바디:

- `name`

응답:

- `categoryId`
- `roomId`
- `name`
- `createdBy`
- `createdAt`

규칙:

- 같은 방 안에서 이름 중복 시 `409`

### 2. 카테고리 목록 조회

- `GET /rooms/{roomId}/bookmark-categories`

응답 항목:

- `categoryId`
- `roomId`
- `name`
- `createdBy`
- `createdAt`

1차에서는 북마크 개수나 추가 메타데이터를 포함하지 않는다.

### 3. 카테고리 이름 변경

- `PATCH /rooms/{roomId}/bookmark-categories/{categoryId}`

요청 바디:

- `name`

규칙:

- 같은 방 안에서 이름 중복 시 `409`
- 다른 방 소속 `categoryId`를 현재 방 경로에서 사용하면 `404`

### 4. 카테고리 삭제

- `DELETE /rooms/{roomId}/bookmark-categories/{categoryId}`

동작:

- 해당 카테고리에 속한 북마크를 먼저 삭제한다.
- 이후 카테고리를 삭제한다.

규칙:

- 다른 방 소속 `categoryId`를 현재 방 경로에서 사용하면 `404`

### 5. 북마크 생성

- `POST /rooms/{roomId}/bookmarks`

요청 바디:

- `googlePlaceId`
- `categoryId`

규칙:

- `categoryId`는 필수
- 지정된 카테고리는 반드시 같은 방 소속이어야 한다.
- 같은 방에 같은 `googlePlaceId` 중복 등록 시 `409`
- 카테고리가 하나도 없는 방에서 생성 시 전용 에러 코드로 `409`

### 6. 북마크 카테고리 변경

- `PATCH /rooms/{roomId}/bookmarks/{bookmarkId}/category`

요청 바디:

- `categoryId`

규칙:

- `bookmarkId`와 `categoryId` 모두 현재 방 소속이어야 한다.
- 다른 방 리소스를 현재 방 경로에서 사용하면 `404`

## 오류 처리

추가가 필요한 대표 오류는 아래와 같다.

- `BOOKMARK_CATEGORY_NOT_FOUND`
- `BOOKMARK_CATEGORY_ALREADY_EXISTS`
- `BOOKMARK_CATEGORY_REQUIRED`
- `BOOKMARK_CATEGORY_EMPTY`

상태 코드 방향:

- 방/카테고리/북마크 소속 불일치 또는 미존재: `404`
- 이름 중복, 장소 중복, 카테고리 없이 북마크 생성 시도: `409`
- 요청 바디 형식 오류, blank 이름 등: `400`

## 테스트 전략

### 서비스 테스트

- 카테고리 생성 성공
- 같은 방 이름 중복 생성 시 `409`
- 카테고리 이름 변경 성공
- 다른 방 카테고리 이름 변경 시 `404`
- 카테고리 삭제 시 북마크도 함께 삭제
- 카테고리 없는 방에서 북마크 생성 실패
- 다른 방 카테고리로 북마크 생성 실패
- 북마크 카테고리 변경 성공
- 다른 방 카테고리로 변경 시 `404`

### 컨트롤러 테스트

- 카테고리 생성 request validation
- 카테고리 이름 변경 request validation
- 북마크 생성 시 `categoryId` 누락 validation
- 상태 코드 및 핵심 응답 필드 검증

### 통합 테스트

- 카테고리 생성 -> 북마크 생성 -> 목록 조회
- 카테고리 이름 변경 반영
- 카테고리 삭제 후 북마크 함께 삭제
- 방 A 카테고리를 방 B 북마크에 지정할 수 없음

## 문서 반영

구현 시 함께 갱신해야 하는 문서:

- `docs/ai/features.md`
- `docs/ai/erd.md`

반영 내용:

- `보관함 카테고리 변경`의 실제 구현 범위를 생성/조회/이름 변경/삭제까지 포함하는지 명확히 정리
- `bookmark_categories` 테이블 추가
- `bookmarks.category` 제거
- `bookmarks.category_id` 추가
- 카테고리 삭제 시 북마크도 함께 삭제되는 정책 명시

## 결정 요약

- 카테고리는 별도 테이블로 관리한다.
- 카테고리는 방마다 독립적으로 생성한다.
- 북마크는 카테고리 없이 존재할 수 없다.
- `ALL`은 저장값이 아니라 클라이언트 조회 의미만 가진다.
- 카테고리 삭제 시 소속 북마크도 함께 삭제한다.
- 삭제 전파는 서비스에서 명시적으로 처리한다.
