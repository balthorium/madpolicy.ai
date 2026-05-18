# Board Schema, Revision, And S3 Layout Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split document structure versioning from content revisioning, and make board resolution exact on `(board, board_revision)` with a storage layout compatible with future S3 use.

**Architecture:** Replace top-level `version` with `schema` plus `revision`, add `board_revision` to game states, and change board storage and lookup to use `boards/<board-id>/<board-revision>.yaml`. Keep the actual adjudication rules unchanged and focus the change on schema, loaders, validation, and fixtures.

**Tech Stack:** Java 21, Spring Boot, Jackson YAML, JUnit 5, Maven

---

## Chunk 1: Schema Model Update

### Task 1: Update top-level board and game-state models

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/model/BoardDocument.java`
- Modify: `src/main/java/ai/madpolicy/diplomacy/model/GameStateDocument.java`
- Test: `src/test/java/ai/madpolicy/diplomacy/model/YamlRoundTripTest.java`

- [ ] **Step 1: Write the failing test**

Add assertions that parsed board YAML exposes `schema()` and `revision()`, and parsed
game-state YAML exposes `schema()`, `revision()`, `board()`, and `boardRevision()`.

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=YamlRoundTripTest`
Expected: failure because the models still expose old top-level fields.

- [ ] **Step 3: Write the minimal implementation**

Replace top-level `version` fields with `schema` and `revision`, and add game-state
`boardRevision` mapped from YAML `board_revision`.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run the same command and expect success.

## Chunk 2: Fixture And Layout Migration

### Task 2: Migrate local YAML fixtures to the new schema

**Files:**
- Move: `data/boards/standard.yaml` -> `data/boards/standard/<revision>.yaml`
- Modify: `data/states/*.yaml`
- Modify: `src/test/resources/regression/*.yaml`
- Test: `src/test/java/ai/madpolicy/diplomacy/model/YamlRoundTripTest.java`

- [ ] **Step 1: Update board fixture structure**

Move the board fixture into a per-board directory and add `schema` plus `revision`.

- [ ] **Step 2: Update game-state fixtures**

Replace top-level `version` with `schema` and `revision`, and add `board_revision`.

- [ ] **Step 3: Re-run parsing coverage**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=YamlRoundTripTest`
Expected: success with migrated fixture parsing.

## Chunk 3: Board Repository Resolution

### Task 3: Update the board catalog to resolve by `(board, board_revision)`

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/app/BoardCatalog.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/app/BoardCatalogTest.java`

- [ ] **Step 1: Write the failing tests**

Cover:
- successful lookup by board ID and revision
- missing revision
- board ID/path mismatch
- board revision/path mismatch

- [ ] **Step 2: Run the targeted test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=BoardCatalogTest`
Expected: failure because the catalog still resolves boards only by ID.

- [ ] **Step 3: Write the minimal implementation**

Resolve boards from `data/boards/<board>/<revision>.yaml` and validate path/content
agreement for both board ID and revision.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run the same command and expect success.

## Chunk 4: Service And Regression Integration

### Task 4: Route service and regression lookup through exact board revision resolution

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/app/AdjudicationServiceTest.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionHarness.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/regression/RegressionCorpusTest.java`

- [ ] **Step 1: Write the failing tests**

Add or update coverage so service and regression execution require both `board` and
`board_revision`.

- [ ] **Step 2: Run the targeted tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjudicationServiceTest,RegressionCorpusTest`
Expected: failure until exact revision resolution is in place.

- [ ] **Step 3: Write the minimal implementation**

Update service and regression helpers to resolve boards by `state.board()` and
`state.boardRevision()`.

- [ ] **Step 4: Run the targeted tests to verify they pass**

Run the same command and expect success.

## Chunk 5: Full Verification

### Task 5: Run complete verification

**Files:**
- No code changes expected

- [ ] **Step 1: Run focused schema and repository tests**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=YamlRoundTripTest,BoardCatalogTest,AdjudicationServiceTest`
Expected: success.

- [ ] **Step 2: Run regression coverage**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest`
Expected: success.

- [ ] **Step 3: Run full test suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && export PATH="$JAVA_HOME/bin:$PATH" && mvn -Dmaven.repo.local=$PWD/.m2 test`
Expected: success.
