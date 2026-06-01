# AGENTS.md

## Purpose
This repository contains a Java 21 + Spring Boot + Maven implementation of a Diplomacy adjudication and turn-orchestration service.

Current service responsibilities:
- `POST /adjudicate` evaluates player-edited orders for move, retreat, and adjustment phases.
- `GET /officiate` returns the canonical initial standard game state from `data/seeds/standard/1.yaml`, overlays initial game options, stamps `phase.started`, and archives the returned state under `data/games/<game_id>/`.
- `POST /officiate` advances the verbatim adjudicated result from `POST /adjudicate` into the next pre-adjudicated state.

Clients submit complete `game_state` documents. The server archives initial `GET /officiate` responses, but later endpoint calls do not load from persisted game state and the server does not maintain turn history, continuity validation, authorization state, or anti-cheat validation.

## Environment
- Repository root: `/Users/adb/Desktop/madpolicy.ai`
- Java version target: 21
- Local Java 21 home:
  - `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
- Maven should be run with a workspace-local repo:
  - `mvn -Dmaven.repo.local=$PWD/.m2 ...`

Before exporting `JAVA_HOME` or modifying `PATH`, first check the live shell:

```bash
which java
java -version
```

If `java` already resolves to Java 21, do not override `JAVA_HOME` or prepend a new Java path. Only apply the exports below when the current shell is not already using Java 21.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Key Docs
- Move adjudication spec:
  - `docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md`
- Officiate endpoint spec:
  - `docs/superpowers/specs/2026-05-31-officiate-endpoint-design.md`
- Historical implementation plans:
  - `docs/superpowers/plans/`

## Important Rule Decisions
- The service boundary is:
  - `adjudicate` evaluates player orders and returns a judged state.
  - `officiate` advances a judged state without player involvement.
- `phase.started` is seconds since epoch. Every `/officiate` response sets it to the current time; `POST /adjudicate` preserves the submitted value.
- `GET /officiate` accepts optional query parameters:
  - `game_id`, defaulting to a random UUID
  - `move_phase_minutes`, defaulting to 15
  - `retreat_phase_minutes`, defaulting to 5
  - `adjustment_phase_minutes`, defaulting to 5
- Game options are top-level `options` on `GameStateDocument` and are carried through `POST /adjudicate` and `POST /officiate` unchanged.
- `GET /officiate` writes the exact returned initial game state to `data/games/<game_id>/<year>_<season>_<step>.yaml`.
- Support-to-hold matches supported units ordered to `hold`, `support`, or `convoy`.
- Support-to-move matching is province-based on the destination side, not exact-position based.
- A dislodged unit:
  - does not defend its own province against the move that dislodges it
  - may still affect adjudication elsewhere in the turn
  - may still cut support in another province, as long as that support is not contributing to the dislodgement
- A fleet attacked by a convoyed army does not have its support cut if:
  - the convoy has exactly one possible route
  - and that support could alter the success of that route by supporting a convoying fleet or an attack on a convoying fleet
- In that single-route convoy exception, the protected support may still apply even if the supporter is dislodged by the convoyed attack.
- Same-position move orders are invalid.
- Convoy endpoints are land positions in coastal provinces.
- Convoy orders must be validated against whether the specific fleet can lie on a possible convoy route.
- Retreat orders are valid only for dislodged units.
- Retreats cannot go to the province of the dislodging unit, occupied provinces, or provinces with standoffs.
- Two or more units retreating to the same province all fail.
- Adjustment orders are limited to `build`, `disband`, and `hold`; all other orders are illegal.
- Build and disband orders are checked for validity before applying any excessive-build, excessive-disband, or civil-disorder selection.
- Players are not required to build as many units as they are permitted to build.
- Civil-disorder and excess-order selection prefer:
  - units or candidate builds furthest from a home supply center
  - fleets before armies on ties
  - province name alphabetically on further ties

## Main Source Areas
- App entry:
  - `src/main/java/ai/madpolicy/diplomacy/DiplomacyApplication.java`
- Models:
  - `src/main/java/ai/madpolicy/diplomacy/model/`
- Board/state indexing:
  - `src/main/java/ai/madpolicy/diplomacy/domain/BoardIndex.java`
  - `src/main/java/ai/madpolicy/diplomacy/domain/GameStateIndex.java`
- Adjudication:
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/OrderValidator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/RetreatAdjudicator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/AdjustmentAdjudicator.java`
  - `src/main/java/ai/madpolicy/diplomacy/adjudication/FailureComment.java`
- Output builders:
  - `src/main/java/ai/madpolicy/diplomacy/io/AdjudicatedStateBuilder.java`
  - `src/main/java/ai/madpolicy/diplomacy/io/NextStateBuilder.java`
- Service layer:
  - `src/main/java/ai/madpolicy/diplomacy/app/AdjudicationService.java`
  - `src/main/java/ai/madpolicy/diplomacy/app/BoardCatalog.java`
- Web layer:
  - `src/main/java/ai/madpolicy/diplomacy/web/AdjudicationController.java`

## Runtime Data
- Boards:
  - `data/boards/standard/1.yaml`
- Initial game-state seeds:
  - `data/seeds/standard/1.yaml`

Runtime initial-state archive:
- `data/games/<game_id>/<year>_<season>_<step>.yaml`

The archive root defaults to `data/games` and can be overridden with `diplomacy.games.root`.

Generated `data/games/` contents are ignored by git. They are not used as the source of truth for `POST /adjudicate` or `POST /officiate`.

## Main Test Areas
- Micro adjudication tests:
  - `src/test/java/ai/madpolicy/diplomacy/adjudication/MoveAdjudicatorMicroTest.java`
- Validator tests:
  - `src/test/java/ai/madpolicy/diplomacy/adjudication/OrderValidatorTest.java`
- REST controller tests:
  - `src/test/java/ai/madpolicy/diplomacy/web/AdjudicationControllerTest.java`
  - `src/test/java/ai/madpolicy/diplomacy/web/OfficiateControllerMockMvcTest.java`
- Regression harness and corpora:
  - `src/test/java/ai/madpolicy/diplomacy/regression/RegressionHarness.java`
  - `src/test/java/ai/madpolicy/diplomacy/regression/RegressionCorpusTest.java`
  - `src/test/java/ai/madpolicy/diplomacy/regression/RetreatRegressionCorpusTest.java`
  - `src/test/java/ai/madpolicy/diplomacy/regression/AdjustmentRegressionCorpusTest.java`
  - `src/test/java/ai/madpolicy/diplomacy/regression/OfficiateRegressionTest.java`

## Regression Fixture Layout
Regression fixtures are organized by endpoint first, then phase:

- `src/test/resources/regression/adjudicate/move`
- `src/test/resources/regression/adjudicate/retreat`
- `src/test/resources/regression/adjudicate/adjustment`
- `src/test/resources/regression/officiate/move`
- `src/test/resources/regression/officiate/retreat`
- `src/test/resources/regression/officiate/adjustment`

All regression tests are REST-based:
- adjudicate regression tests call live `POST /adjudicate` through `YamlAdjudicationHttpClient`
- officiate regression tests call live `POST /officiate` through `YamlAdjudicationHttpClient`

These tests require local port binding and use `YamlAdjudicationHttpClient.canBindLocalPort()` to skip when a local HTTP server cannot be started.

Officiate adjustment requested fixtures are copied from adjudicate adjustment expected fixtures. This reflects the service contract that `POST /officiate` accepts the verbatim adjudicated state produced by `POST /adjudicate`.

## Current Status
As of 2026-06-01:
- move, retreat, and adjustment adjudication are implemented and covered by REST regression tests
- move, retreat, and adjustment officiation are implemented and covered by REST regression tests
- `GET /officiate` returns the `data/seeds/standard/1.yaml` template with request-specific options and `phase.started`
- `GET /officiate` archives the returned initial state under `data/games/<game_id>/`
- `/officiate` stamps `phase.started` on every response and `GET /officiate` includes game options
- `POST /officiate` handles:
  - adjudicated move states to pre-retreat states
  - adjudicated spring retreat states to fall move states
  - adjudicated fall retreat states to fall adjustment states, including control updates
  - adjudicated fall adjustment states to next-year spring move states

Useful verification commands:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=MoveAdjudicatorMicroTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RetreatRegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjustmentRegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=OfficiateRegressionTest
mvn -Dmaven.repo.local=$PWD/.m2 test
```

## Working Notes
- Prefer adding a focused microtest or regression fixture before changing adjudication logic.
- Treat the specs and explicit user decisions as authoritative when the corpus is inconsistent, but verify carefully before changing fixtures.
- Do not change any regression `status` field without explicit user confirmation first.
- The move adjudicator depends on iterative resolution for convoy paradox cases; preserve that structure unless there is strong reason to replace it.
- If Maven cannot resolve dependencies, that is likely an environment/network limitation rather than a known project regression.

## Notes for the Next Agent
- Use `apply_patch` for edits.
- Do not revert unrelated user changes.
- Keep endpoint boundaries clear: `adjudicate` judges player orders; `officiate` advances already-judged state.
