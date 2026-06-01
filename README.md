# madpolicy.ai

Java 21 + Spring Boot + Maven implementation of a Diplomacy adjudication and turn-orchestration service.

## Scope

This repo currently exposes two YAML endpoints:

- `POST /adjudicate` evaluates player-edited orders for move, retreat, and adjustment phases.
- `GET /officiate` returns the canonical initial standard game state, stamps `phase.started`, accepts optional game options as query parameters, and archives the returned state under `data/games/<game_id>/`.
- `POST /officiate` advances an adjudicated state into the next pre-adjudicated state without player involvement.

Clients submit complete `game_state` documents to each request. The server archives the initial state returned by `GET /officiate`, but `POST /adjudicate` and `POST /officiate` do not load from persisted game state, enforce turn continuity, or validate anti-cheat concerns. Static runtime data lives under `data/boards` and `data/seeds`.

Each game state may carry top-level `options`:

- `game_id`
- `move_phase_minutes`
- `retreat_phase_minutes`
- `adjustment_phase_minutes`

`GET /officiate` adds these options to the initial game state. If omitted, the defaults are a random UUID game id, 15-minute move phases, 5-minute retreat phases, and 5-minute adjustment phases. It also writes the exact returned initial state to `data/games/<game_id>/<year>_<season>_<step>.yaml`, for example `data/games/demo/1901_spring_move.yaml`. The archive root defaults to `data/games` and can be overridden with `diplomacy.games.root`. `POST /adjudicate` and `POST /officiate` preserve options unchanged. Every `/officiate` response sets `phase.started` to the current epoch seconds.

The primary behavioral oracle is the REST-based regression corpus in `src/test/resources/regression`.

## Requirements

- Java 21
- Maven

Check that your shell is already using Java 21:

```bash
which java
java -version
```

If it is not, set Java 21 explicitly:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Use a workspace-local Maven repo:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 ...
```

## Build

Compile the project:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 compile
```

Build the jar:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 package
```

## Test

Run focused adjudication tests:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=MoveAdjudicatorMicroTest
```

Run adjudicate regression corpora:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=RetreatRegressionCorpusTest
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=AdjustmentRegressionCorpusTest
```

Run officiate regression corpora:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test -Dtest=OfficiateRegressionTest
```

Run the full test suite:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 test
```

## Manual Testing

Start the service locally for manual testing:

```bash
mvn -Dmaven.repo.local=$PWD/.m2 spring-boot:run
```

In another shell, call the YAML adjudication endpoint with a regression request fixture:

```bash
curl -H 'Content-Type: application/yaml' \
     -H 'Accept: application/yaml' \
     --data-binary @src/test/resources/regression/adjudicate/move/test1_requested.yaml \
     http://localhost:8080/adjudicate \
     -o /tmp/test1_actual.yaml
```

Compare the response to the expected fixture with `dyff`:

```bash
dyff between /tmp/test1_actual.yaml src/test/resources/regression/adjudicate/move/test1_expected.yaml
```

If `dyff` reports no differences, the manual adjudication result matches the expected fixture semantically.

Start a new standard game:

```bash
curl -H 'Accept: application/yaml' \
     'http://localhost:8080/officiate?game_id=demo&move_phase_minutes=15&retreat_phase_minutes=5&adjustment_phase_minutes=5'
```

That call also archives the returned YAML at:

```text
data/games/demo/1901_spring_move.yaml
```

Advance an adjudicated state to the next pre-adjudicated state:

```bash
curl -H 'Content-Type: application/yaml' \
     -H 'Accept: application/yaml' \
     --data-binary @src/test/resources/regression/officiate/adjustment/test26_requested.yaml \
     http://localhost:8080/officiate
```

## Project Layout

- `src/main/java/ai/madpolicy/diplomacy/adjudication/`
  Phase adjudication logic for move, retreat, and adjustment turns.
- `src/main/java/ai/madpolicy/diplomacy/domain/`
  Board and game-state indexing plus normalized order/unit structures.
- `src/main/java/ai/madpolicy/diplomacy/io/`
  Builders for adjudicated states and next pre-adjudicated states.
- `src/main/java/ai/madpolicy/diplomacy/app/`
  Board catalog, service orchestration, and initial-state archiving.
- `src/main/java/ai/madpolicy/diplomacy/web/`
  YAML HTTP controller for `/adjudicate` and `/officiate`.
- `src/test/java/ai/madpolicy/diplomacy/adjudication/`
  Focused adjudication and validator tests.
- `src/test/java/ai/madpolicy/diplomacy/regression/`
  REST regression harness and corpus tests.
- `data/boards/standard/1.yaml`
  Standard board topology.
- `data/seeds/standard/1.yaml`
  Canonical initial standard game state template used by `GET /officiate`; runtime options and `phase.started` are overlaid per request.
- `data/games/`
  Runtime archive for initial game states returned by `GET /officiate`; generated contents are ignored by git.
- `src/test/resources/regression/`
  Endpoint-first regression fixtures:
  - `adjudicate/move`
  - `adjudicate/retreat`
  - `adjudicate/adjustment`
  - `officiate/move`
  - `officiate/retreat`
  - `officiate/adjustment`

## Important Rules

- Support-to-hold matches supported units ordered to `hold`, `support`, or `convoy`.
- Support-to-move matching is province-based at the destination side.
- Same-position move orders are invalid.
- Convoy endpoints are land positions in coastal provinces.
- A dislodged unit does not defend its own province against the move that dislodges it, but may still affect adjudication elsewhere.
- A fleet attacked by a convoyed army does not have its support cut when:
  - the convoy has exactly one possible route
  - and the support could alter the success of that route by supporting a convoying fleet or an attack on a convoying fleet
- Retreat orders are valid only for dislodged units, cannot retreat to the dislodger's province, cannot retreat to occupied provinces or standoff provinces, and contested retreats fail.
- Adjustment orders are limited to `build`, `disband`, and `hold`; all other order types are illegal.
- Build and disband orders are checked for validity before excessive-build, excessive-disband, or civil-disorder selection is applied.
- Players are not required to use every allowed build.
- Civil-disorder and excess-order selection prefer units or candidate builds furthest from a home supply center, then fleets before armies, then province name alphabetically.

## Docs

- Move adjudication spec: `docs/superpowers/specs/2026-04-26-move-adjudication-engine-design.md`
- Officiate endpoint spec: `docs/superpowers/specs/2026-05-31-officiate-endpoint-design.md`
