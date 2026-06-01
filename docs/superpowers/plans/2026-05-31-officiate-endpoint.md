# Officiate Endpoint Implementation Plan

> Historical note: this plan records the original implementation path and follow-on scope. Do not treat the unchecked boxes as the current active task list.

> Current note: this plan began as the implementation plan for initial move-to-retreat officiation. The implemented service now supports `GET /officiate` plus `POST /officiate` for adjudicated move, retreat, and adjustment states. It also stamps `phase.started` on officiate responses, carries top-level game options, and archives the initial state returned by `GET /officiate` under `data/games/<game_id>/`. The endpoint contract and current fixture strategy are captured in `docs/superpowers/specs/2026-05-31-officiate-endpoint-design.md`.

**Goal:** Add `GET /officiate` for the canonical initial game state and `POST /officiate` for advancing adjudicated states to the next pre-adjudicated state.

**Architecture:** Keep the controller thin and put orchestration in a service method beside existing adjudication work. Store the initial seed as runtime data under `data/seeds/standard/1.yaml`, and use `NextStateBuilder` for move-to-retreat, retreat-to-next-step, and adjustment-to-next-year state construction.

**Tech Stack:** Java 21, Spring Boot 3.5, Maven, Jackson YAML, JUnit 5, AssertJ.

---

## Chunk 1: Seed And Endpoint Behavior

### Task 1: Add Officiate HTTP Tests

**Files:**
- Modify: `src/test/java/ai/madpolicy/diplomacy/web/YamlAdjudicationHttpClient.java`
- Modify: `src/test/java/ai/madpolicy/diplomacy/web/AdjudicationControllerTest.java`

- [ ] **Step 1: Add client support for `/officiate`**

Add a shared officiate URI and methods:

```java
public HttpResponse<String> initialStateYaml()
public HttpResponse<String> officiateYaml(String requestBody)
public GameStateDocument officiate(GameStateDocument requestBody)
```

- [ ] **Step 2: Add failing GET test**

Add a test that calls `GET /officiate`, expects `200`, `application/yaml`, and compares the parsed response to `data/seeds/standard/1.yaml`.

- [ ] **Step 3: Add failing POST test**

Add a test that loads `src/test/resources/regression/adjudicate/move/test6_requested.yaml`, adjudicates it through the service path, posts the adjudicated state to `/officiate`, and compares the response to `new NextStateBuilder().build(input, adjudication)`.

- [ ] **Step 4: Run web tests and verify RED**

Run:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjudicationControllerTest
```

Expected: failure because `/officiate` is not implemented and the seed file is missing.

### Task 2: Add Initial State Seed

**Files:**
- Create: `data/seeds/standard/1.yaml`

- [ ] **Step 1: Create the seed file**

Create `data/seeds/standard/1.yaml` as the canonical initial standard game state.

- [ ] **Step 2: Keep seed semantics stable**

Verify the seed has `phase.step: move`, `phase.year: 1901`, pending hold orders for all starting units, and `standoffs: []`.

### Task 3: Implement Officiate Service Methods

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`

- [ ] **Step 1: Add seed loading method**

Add:

```java
public GameStateDocument initialState()
```

This loads `data/seeds/standard/1.yaml` through the existing YAML mapper.

- [ ] **Step 2: Add next-state method**

Add:

```java
public GameStateDocument officiate(GameStateDocument adjudicatedState)
```

For adjudicated move-phase input, treat the supplied state as authoritative and return `NextStateBuilder.build(adjudicatedState)`. Do not re-run adjudication.

- [ ] **Step 3: Preserve existing adjudication method**

Do not change the behavior or return type of `adjudicate(GameStateDocument)`.

### Task 4: Implement Officiate Controller Methods

**Files:**
- Modify: `src/main/java/ai/madpolicy/diplomacy/web/AdjudicationController.java`

- [ ] **Step 1: Add `GET /officiate`**

Return `adjudicationService.initialState()` and produce `application/yaml`.

- [ ] **Step 2: Add `POST /officiate`**

Consume and produce `application/yaml`, accept a `GameStateDocument`, and return `adjudicationService.officiate(gameState)`.

- [ ] **Step 3: Run focused tests and verify GREEN**

Run:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjudicationControllerTest
```

Expected: PASS.

---

## Implemented Follow-On Scope

The implementation was extended beyond the initial plan:

- `POST /officiate` accepts adjudicated move states and returns pre-retreat states.
- `POST /officiate` accepts adjudicated spring retreat states and returns fall move states.
- `POST /officiate` accepts adjudicated fall retreat states and returns fall adjustment states with supply-center controls updated after retreat cleanup.
- `POST /officiate` accepts adjudicated fall adjustment states and returns spring move states for the next year.
- Every `/officiate` response stamps `phase.started` with current epoch seconds.
- `GET /officiate` accepts optional `game_id`, `move_phase_minutes`, `retreat_phase_minutes`, and `adjustment_phase_minutes` query parameters and returns them as top-level `options`.
- `POST /adjudicate` and `POST /officiate` preserve top-level `options` unchanged.
- `GET /officiate` writes the returned initial state to `data/games/<game_id>/<year>_<season>_<step>.yaml`.
- Later endpoint calls remain request-body driven and do not load from persisted game state.
- Regression fixtures are organized by endpoint and phase under:
  - `src/test/resources/regression/adjudicate/{move,retreat,adjustment}`
  - `src/test/resources/regression/officiate/{move,retreat,adjustment}`
- All regression tests are REST-based through `YamlAdjudicationHttpClient`.
- Officiate adjustment requested fixtures are copied from adjudicate adjustment expected fixtures, because `POST /officiate` consumes the verbatim adjudicated state produced by `POST /adjudicate`.

### Task 5: Regression Verification

**Files:**
- No production file changes expected.

- [ ] **Step 1: Run adjudicate regression corpora**

Run:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RetreatRegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjustmentRegressionCorpusTest
```

Expected: PASS.

- [ ] **Step 2: Run officiate regression corpus**

Run:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=OfficiateRegressionTest
```

Expected: PASS.

- [ ] **Step 3: Run focused next-state tests**

Run:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=NextStateBuilderTest
```

Expected: PASS.
