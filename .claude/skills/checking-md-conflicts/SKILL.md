---
name: checking-md-conflicts
description: Use when any markdown file is added, modified, or deleted in this project — checks for dead references, duplicate rules, and cross-document contradictions across AGENTS.md, CONTRIBUTING.md, and docs/ai/ files
---

# Checking MD Conflicts

## Overview

After any markdown change, scan all related MD files for dead references, duplicate rules, and contradictions. Cross-document drift is subtle and accumulates silently.

## When to Use

- Any `.md` file is created, modified, or deleted
- Before committing documentation changes

## Checklist

### 1. Dead References

Find all file path references in MD files and confirm each exists:

```bash
# 참조된 경로 목록 추출
grep -rn '`docs/ai/[^`]*\.md`\|`[A-Z][A-Z_]*.md`' **/*.md docs/**/*.md
```

각 경로를 Glob으로 실제 존재 여부 확인. 존재하지 않으면 → 해당 참조 제거 또는 수정.

### 2. Duplicate Update Rules

여러 파일에 동일한 갱신 규칙이 중복되는지 확인:

- `AGENTS.md` Doc Update Rules
- `docs/ai/README.md` Update Rules

같은 규칙이 2곳 이상에 있으면 → 소유자를 한 파일로 확정하고 나머지에서 제거.

### 3. Duplicate Guidance

아래 패턴을 확인:

- "불일치 보고" 지침이 3곳 이상 반복
- 같은 문서 경로를 여러 파일이 각자 설명
- 같은 행동에 대해 상충하는 지침

### 4. Concept Consistency

삭제된 파일명, 변경된 경로명이 다른 문서에서 여전히 구 이름으로 참조되는지 확인.

## Report Format

점검 후 아래 형식으로 보고:

```
## MD Conflict Check

### Dead References
- file.md:N → `path/to/missing.md` 존재하지 않음

### Duplicate Rules
- "규칙 내용" → AGENTS.md, docs/ai/README.md 중복

### Contradictions
- file1.md:N "X라고 함" vs file2.md:M "Y라고 함"

이슈 없음 / N개 이슈 발견
```

이슈가 없으면 명확히 "이슈 없음"으로 종료. 불필요하게 작업을 블록하지 않는다.

## After Finding Issues

이슈를 찾았으면 보고 후 후속 행동을 분명히 한다.

- 사용자가 점검만 요청한 경우에는 결과를 먼저 보고하고, 수정이 필요한 항목이 있으면 짧게 수정 여부를 묻는다.
- 사용자가 수정까지 함께 요청한 경우에는 별도 확인 없이 바로 수정 가능한 항목부터 처리한다.
- Dead Reference → 실제 경로로 교체하거나 참조를 제거한다.
- Duplicate Rule → 규칙의 소유자 파일을 하나로 정하고, 나머지는 포인터만 남긴다.
- Contradiction → 기준 문서를 명시하고 상충하는 문장을 제거하거나 정리한다.

## Common Mistakes

- 수정된 파일만 확인하고 연관 파일은 건너뜀
- 파일 존재 여부를 기억에 의존해 확인 (반드시 Glob 사용)
- 이슈 발견 시 보고만 하고 후속 조치(수정 여부 확인 또는 즉시 수정)를 생략함
