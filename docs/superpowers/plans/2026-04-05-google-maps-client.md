# Google Maps Client Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `common/client` 아래에 Google Places 검색/상세와 Google Routes 구간 계산용 `RestClient` 기반 초안을 추가한다.

**Architecture:** Google Maps 설정, Places 클라이언트, Routes 클라이언트를 분리한다. 각 클라이언트는 내부 요청/응답 DTO만 책임지고, API 키/필드 마스크/에러 변환은 공통 설정 또는 클라이언트 내부에 캡슐화한다.

**Tech Stack:** Spring Boot 4, Spring RestClient, Jackson, JUnit 5, MockRestServiceServer

---

## Chunk 1: Test Contracts

### Task 1: Places search client contract

**Files:**
- Create: `src/test/java/com/howaboutus/backend/common/client/google/places/GooglePlacesClientTest.java`
- Create: `src/test/java/com/howaboutus/backend/common/client/google/support/TestRestClientFactory.java`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
- [ ] **Step 4: Run test to verify it passes**

### Task 2: Places details and routes client contracts

**Files:**
- Modify: `src/test/java/com/howaboutus/backend/common/client/google/places/GooglePlacesClientTest.java`
- Create: `src/test/java/com/howaboutus/backend/common/client/google/routes/GoogleRoutesClientTest.java`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Write minimal implementation**
- [ ] **Step 4: Run test to verify it passes**

## Chunk 2: Production Implementation

### Task 3: Google Maps configuration and common exception

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/client/google/config/GoogleMapsClientConfig.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/properties/GoogleMapsProperties.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/exception/GoogleApiClientException.java`
- Modify: `src/main/java/com/howaboutus/backend/HowAboutUsBackendApplication.java`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Write only the minimal code required by failing tests**
- [ ] **Step 2: Re-run targeted tests**

### Task 4: Places and Routes DTOs plus client implementations

**Files:**
- Create: `src/main/java/com/howaboutus/backend/common/client/google/places/GooglePlacesClient.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/places/GooglePlacesSearchRequest.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/places/GooglePlaceSummary.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/places/GooglePlaceDetails.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/routes/GoogleRoutesClient.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/routes/GoogleComputeRouteRequest.java`
- Create: `src/main/java/com/howaboutus/backend/common/client/google/routes/GoogleRouteSummary.java`

- [ ] **Step 1: Write only the minimal code required by failing tests**
- [ ] **Step 2: Re-run targeted tests**

## Chunk 3: Final Verification

### Task 5: Regression verification

**Files:**
- Verify only

- [ ] **Step 1: Run targeted Google client tests**
  - Run: `./gradlew test --tests "*GooglePlacesClientTest" --tests "*GoogleRoutesClientTest"`
- [ ] **Step 2: Run broader test suite**
  - Run: `./gradlew test`
