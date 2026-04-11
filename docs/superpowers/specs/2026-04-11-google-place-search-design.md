# Google Place Search Design

**목표:** Google Places API 기반 장소 검색을 도입하되, Google 정책 제약을 피하기 위해 장소 상세 데이터는 영속 저장하지 않고 Redis에 짧은 TTL로 캐시한다.

## 범위

이번 설계는 아래 범위만 다룬다.

- 장소 검색 API `GET /places/search?query=...`
- Google Places API (New) `Text Search (New)` 연동
- `places` 테이블을 영속 참조 테이블로 축소
- Redis 기반 검색 결과 캐시

이번 설계에서 제외한다.

- 장소 상세 조회 API
- Routes API 연동
- 장소 사진 바이너리 프록시
- Place ID 주기 refresh 배치

## 배경

기존 ERD는 `places` 테이블을 Google 장소 데이터 캐시처럼 사용하도록 되어 있었다. 그러나 Google Maps Platform 정책과 서비스 약관 기준으로 Places API 데이터의 영속 저장은 제한되며, `place_id`는 캐시 제한 예외로 저장이 가능하다. 또한 Places API의 위경도는 최대 30일까지만 임시 캐시가 가능하다.

따라서 Google 응답 데이터 전체를 DB에 저장하는 방향은 피하고, `google_place_id`만 영속 저장하는 참조 테이블로 구조를 변경한다.

## 데이터 모델

`places` 테이블은 삭제하지 않고 유지한다. 다만 역할을 "Google 데이터 캐시"가 아니라 "내부 참조 무결성을 위한 영속 매핑 테이블"로 바꾼다.

권장 컬럼은 아래와 같다.

- `id`: 내부 PK
- `google_place_id`: Google Place ID, `UNIQUE`, `NOT NULL`
- `created_at`
- `updated_at`

삭제 대상 컬럼은 아래와 같다.

- `name`
- `address`
- `location`
- `category`
- `rating`
- `photo_reference`
- `last_synced_at`

이유는 아래와 같다.

- `bookmarks`, `schedule_items`가 긴 문자열인 `google_place_id`를 직접 FK로 쓰는 것보다 내부 정수 PK를 참조하는 편이 안정적이다.
- Place ID는 저장 가능하지만 시간이 지나며 obsolete 될 수 있다.
- 추후 Place ID가 바뀌더라도 내부 PK를 유지하면 관계 데이터 변경 범위를 최소화할 수 있다.

## API 설계

엔드포인트:

- `GET /places/search?query=...`

요청 규칙:

- `query`는 필수다.
- 공백만 있는 요청은 허용하지 않는다.

응답 필드 초안:

- `placeId`: 내부 `places.id`
- `googlePlaceId`
- `name`
- `formattedAddress`
- `location.lat`
- `location.lng`
- `primaryType`
- `rating`
- `photoName`

응답에는 UI와 후속 기능에서 바로 쓸 필드만 포함한다. Google 원문 응답 전체를 그대로 노출하지 않는다.

## 처리 흐름

1. 클라이언트가 `GET /places/search?query=...` 호출
2. 서버가 정규화된 query 기준 Redis 캐시 조회
3. 캐시 hit 시 캐시된 검색 결과 반환
4. 캐시 miss 시 Google Places API (New) `Text Search (New)` 호출
5. 결과마다 `google_place_id` 기준으로 `places` 테이블 upsert
6. 내부 `placeId`를 매핑해 응답 DTO 생성
7. 응답 DTO를 Redis에 짧은 TTL로 저장
8. 클라이언트에 결과 반환

핵심 분리는 아래와 같다.

- DB: 참조 무결성 보장
- Redis: Google 응답 캐시

## 캐시 전략

Redis에는 검색 결과만 저장한다.

- 키 예시: `places:search:<normalized-query>`
- 값: API 응답 DTO 리스트
- TTL: 초기값 `5분`

이번 단계에서는 장소별 상세 캐시를 따로 두지 않는다. 필요해지면 `Place Details` 도입 시점에 별도 키 전략을 설계한다.

Redis 장애 시에는 캐시 없이 Google API를 직접 호출하는 폴백을 허용한다. 캐시 계층이 검색 기능 전체를 멈추게 만들지는 않는다.

## 외부 API 클라이언트

현재 단계에서는 `WebClient` 대신 `RestClient`를 사용한다.

이유는 아래와 같다.

- 현재 기능은 Spring MVC 기반의 단건 요청-응답 구조다.
- 장소 검색은 Google 외부 호출 1회가 중심이라 구현 단순성이 중요하다.
- 프로젝트가 Java 21 기반이므로, 필요 시 가상 스레드와 함께 동기 HTTP 호출을 실용적으로 운영할 수 있다.

향후 Places Details 다건 fan-out, Routes 병렬 호출, 비동기 조합이 복잡해지면 `WebClient` 전환 여부를 다시 검토한다.

## Place ID 갱신 정책

이번 단계에서는 `google_place_id` 자동 갱신을 구현하지 않는다.

다만 Place ID는 장기간 운영 중 obsolete 될 수 있으므로 아래 원칙을 둔다.

- 내부 관계는 항상 `places.id`를 기준으로 유지한다.
- 추후 `Place Details` 또는 `Routes` 호출에서 `NOT_FOUND`가 발생하면, 해당 `google_place_id` 갱신 로직을 추가할 수 있도록 설계한다.
- 주기 refresh 배치는 이번 범위에서 제외한다.

## 오류 처리

- `query` 누락 또는 blank: `400 Bad Request`
- Google Places API 호출 실패: `502 Bad Gateway`
- Redis 장애: 캐시 없이 Google 직접 호출로 폴백
- DB upsert 실패: 요청 전체 실패 처리

현재 단계에서는 부분 성공보다 일관성을 우선한다.

## 보안 및 설정

- API Key는 서버 측에서만 사용한다.
- API Key는 환경 변수 또는 외부 설정으로 주입한다.
- Google Maps Platform API Key 제한을 적용한다.
- 엔드포인트 경로에는 `/api`를 붙이지 않는다. API 서브도메인을 사용한다.
- 서버 스레드 모델은 Java 21 가상 스레드 활성화를 기본값으로 검토한다.

## 테스트 전략

단위 테스트:

- query 검증
- 캐시 hit/miss 분기
- Google 응답 DTO 매핑

통합 테스트:

- `/places/search` 엔드포인트 검증
- `places` upsert 동작 검증
- Redis 캐시 연동 검증

외부 API 테스트:

- 실제 Google 호출은 하지 않는다.
- `RestClient` 호출은 mock 또는 stub 서버로 검증한다.

## 문서 반영 필요 사항

구현 시 아래 문서를 함께 갱신해야 한다.

- `docs/ai/features.md`: 장소 검색 설명을 Redis 캐시 구조에 맞게 수정
- `docs/ai/erd.md`: `places` 테이블 컬럼 및 역할 수정

## 근거 문서

- Google Place IDs: https://developers.google.com/maps/documentation/places/web-service/place-id
- Google Places API Policies: https://developers.google.com/maps/documentation/places/web-service/policies
- Google Maps Platform Service Specific Terms: https://cloud.google.com/maps-platform/terms/maps-service-terms/index-20240522
- Spring REST Clients: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring Boot virtual threads: https://docs.spring.io/spring-boot/reference/features/spring-application.html
