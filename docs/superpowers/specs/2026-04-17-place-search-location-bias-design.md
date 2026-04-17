# Place Search Location Bias 설계

**목표:** 장소 텍스트 검색 API에 현재 위치 좌표와 반경을 선택적으로 받아 Google Places API의 `locationBias.circle`을 적용한다.

## 배경

기존 `GET /places/search?query=...` 는 위치 정보 없이 전역 검색을 수행했다. 사용자의 현재 위치 근처 결과를 우선시하려면 Google Places API (New) Text Search의 `locationBias` 파라미터를 활용해야 한다.

### API 제약 사항

Google Places API (New) Text Search의 위치 필터는 두 종류다:

- **`locationBias.circle`**: 원형 반경 지정 (0~50,000m). 결과를 해당 영역 근방으로 **편향**시키지만 밖의 결과도 포함될 수 있음.
- **`locationRestriction.rectangle`**: 결과를 사각형 안으로 **엄격히 제한**. 원형 미지원.

반경 기반 제한에 가장 자연스러운 방식은 `locationBias.circle`이다. `locationRestriction`은 원형을 지원하지 않으므로 채택하지 않는다.

## API 설계

엔드포인트:

```
GET /places/search?query=일본 맛집&latitude=37.5&longitude=127.0&radius=3000
```

파라미터:

| 파라미터 | 타입 | 필수 | 기본값 | 제약 |
|---------|------|------|--------|------|
| `query` | String | 필수 | - | 공백 불가 |
| `latitude` | Double | 선택 | - | -90~90 |
| `longitude` | Double | 선택 | - | -180~180 |
| `radius` | Double | 선택 | 5000.0 | 0~50000 |

검증 규칙:

- `latitude`, `longitude`는 둘 다 있거나 둘 다 없어야 한다. 하나만 제공 시 `400 Bad Request`.
- `latitude`, `longitude`가 없으면 위치 편향 없이 기존 방식으로 검색.
- `radius`는 `latitude`/`longitude` 없이 단독 제공 시 무시.

## 처리 흐름

```
Client
  └─ GET /places/search?query=...&latitude=...&longitude=...&radius=...
       ↓
PlaceController
  └─ 파라미터 수신 및 검증 (lat/lng 쌍 여부)
       ↓
PlaceSearchService
  └─ search(query, latitude, longitude, radius) 호출
       ↓
GooglePlaceSearchClient
  └─ latitude != null → locationBias.circle 포함 요청
     latitude == null → 기존 요청 (locationBias 없음)
       ↓
Google Places API (New) Text Search
```

## GoogleTextSearchRequest 변경

위치 정보가 있을 때 직렬화 결과:

```json
{
  "textQuery": "일본 맛집",
  "languageCode": "ko",
  "locationBias": {
    "circle": {
      "center": {
        "latitude": 37.5,
        "longitude": 127.0
      },
      "radius": 5000.0
    }
  }
}
```

위치 정보가 없을 때:

```json
{
  "textQuery": "일본 맛집",
  "languageCode": "ko"
}
```

`locationBias`가 null이면 Jackson이 직렬화에서 제외한다 (`@JsonInclude(NON_NULL)` 또는 record null 필드 처리).

## 컴포넌트별 변경 요약

### `GoogleTextSearchRequest`

```
record GoogleTextSearchRequest(String textQuery, String languageCode, LocationBias locationBias)
  record LocationBias(Circle circle)
    record Circle(LatLng center, Double radius)
      record LatLng(Double latitude, Double longitude)
```

팩토리 메서드:
- `withKorean(String textQuery)` → 기존 유지, locationBias = null
- `withKoreanAndLocation(String textQuery, double lat, double lng, double radius)` → locationBias 포함

### `GooglePlaceSearchClient`

```java
public List<PlaceItem> search(String query, Double latitude, Double longitude, Double radius)
```

`latitude != null`이면 `GoogleTextSearchRequest.withKoreanAndLocation(...)` 사용.
`latitude == null`이면 `GoogleTextSearchRequest.withKorean(...)` 사용.

### `PlaceSearchService`

```java
public List<PlaceSearchResult> search(String query, Double latitude, Double longitude, Double radius)
```

단순 위임. 로직 없음.

### `PlaceController`

```java
@GetMapping("/places/search")
public List<PlaceSearchResponse> search(
    @RequestParam @NotBlank String query,
    @RequestParam(required = false) Double latitude,
    @RequestParam(required = false) Double longitude,
    @RequestParam(required = false, defaultValue = "5000.0") Double radius
)
```

lat/lng 쌍 검증: 하나만 있으면 `400 Bad Request` 반환.

## 오류 처리

| 상황 | HTTP 상태 |
|------|----------|
| `query` 누락 또는 blank | 400 Bad Request |
| `latitude`만 있고 `longitude` 없음 (또는 반대) | 400 Bad Request |
| `radius` > 50000 | 400 Bad Request |
| Google API 호출 실패 | 502 Bad Gateway |

## 테스트 전략

단위 테스트:

- `GoogleTextSearchRequest`: locationBias 포함/미포함 팩토리 메서드 검증
- `GooglePlaceSearchClient`: latitude null/non-null 시 올바른 요청 생성 검증
- `PlaceController`: lat만/lng만 제공 시 400 반환, 정상 파라미터 시 서비스 위임 검증

기존 테스트:

- `search(query)` 동작은 `latitude = null`로 호출하는 것과 동일하므로 기존 테스트는 유지.
