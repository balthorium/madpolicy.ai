# Officiate Endpoint Design

## Context

The service currently exposes `POST /adjudicate` for move-phase adjudication. That endpoint accepts a pre-adjudicated move-phase `game_state` and returns an adjudicated move-phase `game_state`.

The application also needs an endpoint named `officiate` for turn orchestration:

- provide the canonical initial state for a new standard game
- advance an adjudicated turn output into the pre-adjudicated state for the next turn step

The service is intentionally stateless. It owns static reference data only:

- board definitions in `data/boards`
- initial game-state seeds in `data/seeds`

Clients own game persistence and submit complete `game_state` documents to each endpoint. The server does not maintain game ids, saved game directories, turn history, continuity validation, authorization state, or anti-cheat validation.

## Service Boundary

The application separates player-order resolution from automatic game administration.

### `adjudicate`

`adjudicate` evaluates player edits to a game state.

It accepts a pre-adjudicated state whose orders were authored or updated by players, resolves those orders for the current phase, and returns a judged state describing what happened. The judged state preserves the phase's submitted orders and records adjudication outcomes such as statuses, comments, dislodgements, and standoffs.

Conceptually:

`Given these submitted orders, what is the ruling?`

### `officiate`

`officiate` advances the game without player involvement.

It accepts the verbatim judged result from `adjudicate` and performs the automatic bookkeeping needed to create the next player-editable state. Depending on phase, that bookkeeping may include relocating units, carrying or clearing dislodgements, resetting or creating orders for the next phase, updating phase/season/year, updating supply-center ownership, and computing build or disband obligations.

Conceptually:

`Given that ruling, what state do players edit next?`

This boundary should remain stable as new phase support is added. Future retreat and winter build work should extend phase-specific adjudication in `adjudicate` and phase-transition orchestration in `officiate`, rather than mixing those responsibilities.

## Endpoint Contract

### `GET /officiate`

Returns the initial standard board game state as YAML.

The seed lives at:

`data/seeds/standard/1.yaml`

This location mirrors the board catalog path `data/boards/standard/1.yaml`. The seed is runtime data, not a regression fixture or example game instance.

The initial state is:

- `kind: game_state`
- `schema: 1`
- `revision: 1`
- `board: standard`
- `board_revision: 1`
- `phase.season: spring`
- `phase.step: move`
- `phase.year: 1901`
- every starting unit has a pending hold order
- `standoffs: []`

### `POST /officiate`

Accepts an adjudicated `game_state` as YAML and returns the pre-adjudicated state for the next turn step.

For the current move-phase implementation, this means:

- successful move orders relocate their units
- dislodged units remain present with `dislodged_by`
- dislodged units receive pending `disband` orders by default, which clients may change to valid retreat orders
- non-dislodged units do not receive player-actionable retreat orders
- move-order fields and failure comments are removed
- top-level standoffs come from the adjudication outcome
- the returned phase has `step: retreat`

This endpoint uses the existing adjudication result semantics and `NextStateBuilder` behavior. It does not introduce retreat adjudication, build/disband adjudication, supply-center ownership updates, victory detection, or season/year progression.

## Design Choices

Use one resource name with HTTP method semantics:

- `GET /officiate` starts a new game from the canonical seed
- `POST /officiate` advances an adjudicated state into the next pre-adjudicated state

This avoids wrapper request types and keeps the YAML response shape as a plain `GameStateDocument`.

`POST /officiate` treats its input as an already-adjudicated state. It does not re-run adjudication and does not require players to edit the adjudicated state before submission.

## Testing

Regression fixtures are organized by endpoint first, then phase:

- `src/test/resources/regression/adjudicate/move`
- `src/test/resources/regression/adjudicate/retreat`
- `src/test/resources/regression/adjudicate/adjustment`
- `src/test/resources/regression/officiate/move`
- `src/test/resources/regression/officiate/retreat`
- `src/test/resources/regression/officiate/adjustment`

Existing move, retreat, and adjustment adjudication corpora live under `regression/adjudicate`. Officiate regression fixtures use the same requested/expected naming convention, where `*_requested.yaml` is the verbatim adjudicated state submitted to `POST /officiate` and `*_expected.yaml` is the pre-adjudicated state returned for the next turn step.

For the current implementation, only move-to-retreat officiation has executable fixtures. Those fixtures should be generated from adjudicated move cases that contain at least one `dislodged_by`, because no-dislodgement move turns will later advance directly past retreat when phase progression is implemented.

Add focused HTTP tests that prove:

- `GET /officiate` returns YAML equal to `data/seeds/standard/1.yaml`
- `POST /officiate` accepts an adjudicated move-phase state and returns the same next-state shape currently produced by `NextStateBuilder`
- existing `POST /adjudicate` behavior remains unchanged

Run the focused web tests, the adjudicate move regression corpus, and the officiate move regression corpus after implementation.
