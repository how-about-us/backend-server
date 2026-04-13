# Google Place Detail Design

**목표:** Google Places API 기반 장소 상세 조회를 추가하되, 프론트가 바로 사용할 수 있는 상세 카드형 응답으로 정리하고, Redis 캐시 장애가 전체 조회 실패로 이어지지 않도록 fail-open 전략을 유지한다.

## 범위

이번 설계는 아래 범위만 다룬다.

- 장소 상세 조회 API `GET /places/{googlePlaceId}`
- Google Places API (New) `Place Details (New)` 연동
- 장소 상세 응답의 Redis 캐시
- 검색 API와 동일한 외부 API 오류 처리 정책 적용

이번 설계에서 제외한다.

- 장소 검색 API 구조 변경
- 장소 사진 바이너리 프록시
- 북마크, 일정, 채팅 공유에서의 상세 조회 재사용
- 장소 상세 캐시 무효화 또는 사전 적재

## 배경

현재 프로젝트에는 장소 검색 API만 구현되어 있고, `docs/ai/features.md`와 `docs/ai/erd.md`에는 장소 상세 조회가 별도 기능으로 정의되어 있다. 또한 장소 데이터는 DB에 영속 캐시하지 않고, `google_place_id`를 기준으로 필요한 시점에 외부 API를 조회하는 구조를 채택하고 있다.

이 구조에서는 장소 상세 조회도 검색과 마찬가지로 "우리 서비스 응답 계약"을 먼저 정하고, Google Places 상세 응답은 내부 DTO로 흡수하는 편이 안정적이다. 그래야 프론트가 외부 API 스키마 변화에 직접 노출되지 않고, 이후 북마크·일정·장소 카드 공유 기능에서도 동일한 상세 DTO를 재사용할 수 있다.

## API 설계

엔드포인트:

- `GET /places/{googlePlaceId}`

요청 규칙:

- `googlePlaceId`는 path variable로 받는다.
- blank 또는 공백만 있는 값은 허용하지 않는다.

응답 필드:

- `googlePlaceId`
- `name`
- `formattedAddress`
- `location.lat`
- `location.lng`
- `primaryType`
- `rating`
- `phoneNumber`
- `websiteUri`
- `googleMapsUri`
- `regularOpeningHours.weekdayDescriptions`
- `photoNames`

응답은 상세 카드형을 기준으로 제한한다. Google 상세 응답 전체를 그대로 노출하지 않고, 현재 UI와 후속 기능에서 바로 쓸 수 있는 필드만 포함한다.

## 컴포넌트 설계

현재 검색 API의 계층 분리를 유지하면서 상세 조회 전용 경계를 추가한다.

- `PlaceController`
  - `GET /places/{googlePlaceId}` 엔드포인트 추가
  - 서비스 결과를 `PlaceDetailResponse`로 변환
- `PlaceDetailService`
  - 장소 상세 조회 유스케이스 담당
  - Redis 캐시와 Google 상세 조회를 조합
- Google Places 상세 조회 클라이언트
  - 검색 전용 클라이언트/field mask와 분리해 상세 조회를 담당
  - `googlePlaceId`를 기반으로 `Place Details (New)` 호출
- `PlaceDetailResult`
  - 외부 응답을 서비스 내부의 안정적인 DTO로 매핑

검색과 상세 조회는 field mask와 응답 목적이 다르므로, 동일한 클라이언트에 무리하게 섞기보다 상세 조회용 요청/응답 경계를 별도로 두는 쪽을 권장한다.

## 캐시 전략

장소 상세 조회는 `googlePlaceId` 기준으로 Redis에 캐시한다.

- 캐시 이름: 장소 상세 전용 cache name 사용
- 캐시 키: `googlePlaceId`
- TTL: `3시간`

구현 방식은 서비스 메서드 레벨의 `@Cacheable`을 기본으로 한다. 이번 요구사항은 전형적인 read-through 캐시 패턴이라 별도 수동 Redis 접근 코드 없이도 충분히 구현 가능하다.

캐시 동작 원칙은 아래와 같다.

1. 캐시 hit면 캐시된 상세 응답 반환
2. 캐시 miss면 Google Places 상세 조회 호출
3. 조회 성공 시 결과를 캐시에 저장하고 응답 반환
4. Redis 읽기/쓰기 중 에러가 나면 캐시 미스처럼 취급하고 계속 진행

즉, Redis는 성능 최적화 계층이며, Redis 장애만으로 장소 상세 조회 기능 전체가 실패하지 않도록 fail-open 전략을 유지한다.

## 외부 API 연동

Google Places 상세 조회는 검색과 별도의 field mask를 사용한다.

필요 필드는 아래 범위를 기본값으로 둔다.

- `id`
- `displayName`
- `formattedAddress`
- `location`
- `primaryType`
- `rating`
- `nationalPhoneNumber`
- `websiteUri`
- `googleMapsUri`
- `regularOpeningHours.weekdayDescriptions`
- `photos.name`

상세 조회용 field mask를 검색용 설정과 분리하면, 검색 응답에는 불필요한 필드가 늘어나지 않고, 상세 조회에서만 필요한 데이터를 명확하게 요청할 수 있다.

## 오류 처리

- `googlePlaceId`가 blank이거나 유효하지 않은 입력이면 `400 Bad Request`
- Google Places API 호출 실패 시 `502 Bad Gateway`
- Redis 장애 시 요청은 계속 진행하고 Google API 호출로 폴백

오류 처리 정책은 기존 검색 API와 일관되게 유지한다. 외부 API 실패만 `502`로 간주하고, 캐시 계층 장애는 기능 실패 사유로 승격하지 않는다.

## 테스트 전략

단위 테스트:

- Google 상세 응답을 `PlaceDetailResult`로 올바르게 매핑하는지 검증
- 서비스가 클라이언트 응답을 상세 결과로 변환하는지 검증

컨트롤러 테스트:

- `GET /places/{googlePlaceId}` 정상 응답 검증
- blank path 값 또는 잘못된 요청 형식에 대한 `400` 검증
- 외부 API 예외 발생 시 `502` 검증

외부 API 클라이언트 테스트:

- `Place Details (New)` 엔드포인트로 요청하는지 검증
- API key와 상세 조회용 field mask 헤더를 올바르게 전송하는지 검증

캐시 검증:

- 서비스 호출 횟수 기준으로 캐시 hit 시 외부 클라이언트 재호출이 없는지 검증
- 캐시 에러가 발생해도 서비스가 실패하지 않고 외부 조회로 진행되는지 검증

## 문서 반영 필요 사항

구현 시 아래 문서를 함께 갱신해야 한다.

- `docs/ai/features.md`: 장소 상세 조회의 응답 범위와 캐시 TTL 반영
- `docs/ai/erd.md`: Redis 키의 장소 상세 TTL을 `3시간`으로 갱신

