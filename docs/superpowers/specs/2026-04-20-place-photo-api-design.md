# Place Photo API 설계

**날짜:** 2026-04-20
**브랜치:** feature/place
**관련 이슈:** #15 (장소 사진 API 호출 추가), #16 (장소 캐싱 문제 해결)

---

## 배경

기존 장소 검색/상세 조회 API는 Google Places API에서 받은 `photoName`(사진 리소스 이름)을 그대로 클라이언트에 반환했다. 클라이언트가 실제 사진 URL을 얻으려면 Google Photo Media API를 직접 호출해야 했으나, API 키 노출 문제로 서버가 중계해야 한다.

또한 `photoName`은 일시적인 토큰이므로, 장기 캐시에 저장하면 만료 시 사진 조회에 실패한다. (이슈 #16)

---

## 결정된 설계

### 엔드포인트

| 메서드 | 경로 | 설명 | 캐시 |
|--------|------|------|------|
| GET | `/places/search` | 장소 검색. 결과마다 썸네일용 `photoName` 1개 포함 | 없음 |
| GET | `/places/{googlePlaceId}` | 장소 상세. `photoNames` 목록 포함 | `place:detail` TTL **5분** |
| GET | `/places/photos?name={photoName}` | `photoName` → `photoUrl` 변환 | 없음 |

### 클라이언트 흐름

```
1. GET /places/search 또는 /places/{id}
   → 응답에 photoName(s) 포함

2. GET /places/photos?name={photoName}
   → 서버가 Google Photo Media API 호출
   → { photoUrl: "https://lh3.googleusercontent.com/..." } 반환

3. 클라이언트가 photoUrl로 이미지 직접 로드
```

### 캐싱 전략

- `place:detail` TTL을 **3h → 5분**으로 단축: `photoName` 만료 위험 제거
- `/places/photos` 엔드포인트는 캐시 없음: 같은 photoName의 5분 내 중복 요청 가능성이 낮고, 캐시 관리 복잡도 대비 효용이 적음

### 고려하고 제외한 방안

| 방안 | 이유 |
|------|------|
| photoNames를 캐시에서 제외하고 매번 Google 호출 | photoName 만료 시 어차피 Google API를 다시 호출해야 하므로 API 비용 측면에서 동일. 복잡도만 증가 |
| place:photo 캐시 (5분) 추가 | 같은 photoName의 단기 중복 요청 가능성이 낮아 실효성 부족 |
| 서버에서 photoUrl까지 해결 후 캐시 | 검색 결과 N개에 대해 N번 Photo API 호출 발생 → 응답 지연. 레이지 로딩 불가 |

---

## 신규 컴포넌트

### 프로덕션 코드

| 파일 | 역할 |
|------|------|
| `common/integration/google/dto/GooglePlacePhotoResponse.java` | Photo Media API 응답 DTO `{ name, photoUri }` |
| `common/integration/google/GooglePlacePhotoClient.java` | `GET /v1/{photoName}/media?skipHttpRedirect=true` 호출 |
| `places/service/PlacePhotoService.java` | `GooglePlacePhotoClient` 래핑 서비스 |
| `places/controller/dto/PlacePhotoResponse.java` | 응답 DTO `{ photoUrl }` |

### 기존 코드 변경

| 파일 | 변경 내용 |
|------|-----------|
| `CachePolicy.java` | `PLACE_DETAIL` TTL `Duration.ofHours(3)` → `Duration.ofMinutes(5)` |
| `PlaceController.java` | `GET /places/photos` 엔드포인트 추가, `PlacePhotoService` 주입 |

---

## 테스트 계획

- `GooglePlacePhotoClient` 단위 테스트: Photo Media API 호출 검증
- `PlacePhotoService` 단위 테스트: 클라이언트 위임 검증
- `PlaceControllerTest`: `GET /places/photos` 정상/오류 케이스
- `PlaceDetailCachingTest`: TTL 5분 적용 확인
