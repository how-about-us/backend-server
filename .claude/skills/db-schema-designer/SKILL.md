---
name: db-schema-designer
description: >
  Use when designing or modifying database schemas, writing Flyway migration files, planning indexes,
  or working with PostGIS spatial data in this project. Covers ERD design, migration naming conventions,
  index design checklists, and spatial type rules.
  Make sure to use this skill whenever the user mentions creating tables, altering columns, writing
  migration files, designing relationships, adding indexes, or working with location/spatial data.
---

# DB Schema Designer

## 역할 (Role)

PostgreSQL 17 + PostGIS 3.5 환경에서 how-about-us-backend의 데이터 모델을 설계하고 마이그레이션을 관리한다. **운영 중인 DB를 안전하게 변경하는 것**과 **공간 데이터를 올바르게 다루는 것**을 최우선으로 한다.

---

## 원칙 (Principles)

1. **마이그레이션은 되돌릴 수 없다고 가정하고 작성한다** — 프로덕션 DB에 적용된 마이그레이션은 수정이 아닌 새 마이그레이션으로 보완해야 한다. 기존 파일 수정은 Flyway 체크섬 불일치로 애플리케이션 기동 실패를 유발한다.

2. **공간 데이터는 DB에 위임한다** — Java에서 Haversine 공식으로 거리 계산하면 인덱스를 활용하지 못한다. `ST_DWithin`, `ST_Distance` 같은 PostGIS 함수와 GIST 인덱스를 사용해 DB가 처리하도록 한다.

3. **인덱스는 쿼리 패턴 기반으로 설계한다** — 모든 컬럼에 인덱스를 붙이면 쓰기 성능이 저하된다. 자주 조회되는 조건 컬럼, 정렬 컬럼, 외래 키에만 선별적으로 인덱스를 생성한다.

4. **ERD 문서와 코드를 항상 동기화한다** — 마이그레이션을 작성했으면 반드시 `docs/ai/erd.md`도 함께 갱신한다. 문서와 실제 스키마의 불일치는 향후 작업에서 잘못된 결정을 유발한다.

5. **NOT NULL 제약과 기본값을 명시한다** — 컬럼 추가 시 기존 데이터를 위한 `DEFAULT` 값이나 `NOT NULL` 여부를 명확히 결정한다. 대용량 테이블에 `NOT NULL` 컬럼을 기본값 없이 추가하면 마이그레이션이 실패한다.

---

## 작업 전 체크리스트

- [ ] 현재 ERD를 `docs/ai/erd.md`에서 확인한다.
- [ ] 마이그레이션 파일 번호 순서를 `src/main/resources/db/migration/`에서 확인한다.
- [ ] 이 변경이 기존 엔티티 클래스와 충돌하는지 검토한다.
- [ ] 대용량 테이블 컬럼 추가/변경인 경우 Lock 없는 방식(`CONCURRENTLY`)을 검토한다.

## 작업 후 체크리스트

- [ ] 마이그레이션 파일 이름이 네이밍 규칙을 따르는지 확인한다.
- [ ] `docs/ai/erd.md`를 갱신했는지 확인한다.
- [ ] 공간 컬럼이 있으면 GIST 인덱스가 포함되어 있는지 확인한다.
- [ ] 외래 키에 인덱스가 생성되어 있는지 확인한다.
- [ ] 로컬에서 `./gradlew bootRun`으로 마이그레이션이 정상 적용되는지 확인한다.

---

## 마이그레이션 네이밍 규칙

```
V{버전}__{설명}.sql

예시:
V1__create_users_table.sql
V2__create_meetings_table.sql
V3__add_location_to_meetings.sql
V4__add_index_meetings_location.sql
```

- 버전은 단조 증가 정수 (소수점 버전 `V1.1` 지양)
- 설명은 snake_case, 동사 시작 (`create_`, `add_`, `alter_`, `drop_`)
- 언더스코어 두 개(`__`)로 버전과 설명 구분

---

## PostGIS 공간 타입 규칙

```sql
-- 공간 컬럼 정의
ALTER TABLE meetings
ADD COLUMN location geometry(Point, 4326) NOT NULL;

-- GIST 인덱스 (공간 쿼리 성능의 핵심)
CREATE INDEX idx_meetings_location ON meetings USING GIST(location);

-- 올바른 거리 기반 조회 패턴
SELECT * FROM meetings
WHERE ST_DWithin(
    location::geography,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
    :radiusMeters
);
-- ⚠️ Java Haversine 계산 금지 — 인덱스 미사용으로 풀스캔 발생
```

Java/JPA에서는 `hibernate-spatial`의 `Point` 타입 사용:

```java
import org.locationtech.jts.geom.Point;

@Column(columnDefinition = "geometry(Point, 4326)")
private Point location;
```

---

## 인덱스 설계 체크리스트

| 인덱스 대상 | 인덱스 타입 | 이유 |
|---|---|---|
| 공간 컬럼 (`location` 등) | `GIST` | PostGIS 공간 연산 최적화 |
| 자주 쓰이는 WHERE 컬럼 (`status`, `organizer_id` 등) | B-Tree | 등치/범위 조건 최적화 |
| 외래 키 컬럼 | B-Tree | JOIN 성능 및 참조 무결성 검사 최적화 |
| 정렬/페이지네이션 컬럼 (`created_at` 등) | B-Tree | ORDER BY + LIMIT 최적화 |
| 복합 조건 (`status` + `created_at`) | 복합 B-Tree | 선택도가 낮은 경우 복합 인덱스 고려 |

---

## 마이그레이션 안전 작성 패턴

```sql
-- ✅ 기존 데이터 있는 테이블에 NOT NULL 컬럼 추가 — 안전한 방식
ALTER TABLE meetings ADD COLUMN max_participants INTEGER;
UPDATE meetings SET max_participants = 10 WHERE max_participants IS NULL;
ALTER TABLE meetings ALTER COLUMN max_participants SET NOT NULL;
ALTER TABLE meetings ALTER COLUMN max_participants SET DEFAULT 10;

-- ✅ 인덱스 추가 (락 없이 — 운영 환경 고려)
CREATE INDEX CONCURRENTLY idx_meetings_status ON meetings(status);

-- ❌ 실수 패턴: 기본값 없이 NOT NULL 컬럼 추가 (기존 행 있으면 실패)
ALTER TABLE meetings ADD COLUMN new_col VARCHAR(100) NOT NULL;
```

---

## 금지 사항 (Never Do)

| 금지 행동 | 이유 |
|---|---|
| 기존 마이그레이션 파일 수정 | Flyway 체크섬 불일치로 애플리케이션 기동 실패 |
| Java에서 Haversine으로 거리 계산 | GIST 인덱스 미사용, 전체 행 로드 후 계산 |
| 외래 키 컬럼에 인덱스 누락 | JOIN 시 풀스캔 발생, 삭제 작업 시 락 경합 |
| `docs/ai/erd.md` 미갱신 후 마이그레이션 완료 처리 | 문서-코드 불일치로 이후 작업에서 잘못된 결정 유발 |
| 대용량 테이블에 일반 `CREATE INDEX` | 테이블 락 발생 → 운영 중단 |

---

## 참고 문서

| 문서 | 경로 | 읽는 시점 |
|------|------|-----------|
| ERD 명세서 | `docs/ai/erd.md` | 테이블 구조, 컬럼, 관계 파악 및 갱신 시 |
| 기능 명세서 | `docs/ai/features.md` | 데이터 모델 변경의 기능적 배경 확인 시 |

---

## 이 프로젝트 특이사항

- **Docker 이미지**: `postgis/postgis:17-3.5` 사용 (일반 postgres 이미지 사용 불가)
- **dev 환경 포트**: PostgreSQL `5433` (기본 5432 아님)
- **마이그레이션 경로**: `src/main/resources/db/migration/`
- **공간 확장**: `CREATE EXTENSION IF NOT EXISTS postgis;`는 초기 마이그레이션에서 처리
