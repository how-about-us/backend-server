# Google Maps Client Design

## 목적

`common/client` 아래에 Google Places API와 Google Routes API를 호출하는 공통 클라이언트 초안을 추가한다.
이번 범위는 아래 세 가지 외부 호출만 포함한다.

- Places Text Search 기반 장소 검색
- Places Place Details 기반 장소 상세 조회
- Routes Compute Routes 기반 두 지점 간 구간 계산

저장, Redis 캐시, 도메인 서비스 로직은 이번 범위에서 제외한다.

## 배경

`docs/ai/features.md`와 `docs/ai/api-spec.md` 기준으로 장소 검색/상세와 일정 구간 이동 정보 계산이 필요하다.
`docs/ai/erd.md` 기준으로 Google 정책상 영구 저장이 제한되는 값이 많으므로, 외부 클라이언트는 조회 전용 책임으로 분리하는 것이 적절하다.

## 설계

### 패키지 구조

```text
common/client/google/
  config/
  exception/
  properties/
  places/
  routes/
```

### 구성 요소

- `GoogleMapsProperties`
  - `google.maps.api-key`
  - `google.maps.places-base-url`
  - `google.maps.routes-base-url`
- `GoogleMapsClientConfig`
  - Google API 호출용 `RestClient.Builder` 기반 설정
  - 기본 헤더와 타임아웃 적용
- `GooglePlacesClient`
  - `searchText(...)`
  - `getPlaceDetails(...)`
- `GoogleRoutesClient`
  - `computeRoute(...)`
- `GoogleApiClientException`
  - 외부 API 호출 실패를 감싸는 공통 런타임 예외

### DTO 방향

- 외부 API 응답을 그대로 노출하지 않고, 우리 서비스에서 바로 쓰기 쉬운 내부 DTO로 한 번 감싼다.
- Places 응답은 `googlePlaceId`, `displayName`, `formattedAddress`, `location`, `rating`, `photoNames` 중심으로 축약한다.
- Routes 응답은 `distanceMeters`, `duration`, `polyline`, `travelMode` 중심으로 축약한다.

### 에러 처리

- `RestClient` 호출 중 4xx/5xx 또는 직렬화 실패가 발생하면 `GoogleApiClientException`으로 변환한다.
- 예외 메시지에는 API 종류와 호출 목적이 드러나도록 포함한다.

### 테스트 전략

- `MockRestServiceServer`로 Places 검색, Places 상세, Routes 계산 성공 케이스를 각각 검증한다.
- 4xx/5xx 응답 시 공통 예외로 변환되는지 검증한다.
- 설정 바인딩 자체보다는 클라이언트 호출 계약에 집중한다.

## 제외 범위

- Redis 캐시 적재
- DB 저장 및 upsert
- 서비스/컨트롤러 계층 연결
- Google OAuth 관련 연동

## 문서 영향

이번 작업은 외부 클라이언트 초안 추가로, 기능/API/ERD 자체 변경은 없다.
다만 구현 결과로 설정 키가 추가되므로 필요 시 운영 문서나 환경 변수 문서를 후속으로 보강한다.
