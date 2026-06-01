# Officiate Endpoint Design

## Context

The service exposes `POST /adjudicate` for phase adjudication. That endpoint accepts a pre-adjudicated `game_state` for move, retreat, or adjustment phases and returns an adjudicated `game_state` for the same phase.

The application also needs an endpoint named `officiate` for turn orchestration:

- provide the canonical initial state for a new standard game
- advance an adjudicated turn output into the pre-adjudicated state for the next turn step

The service owns static reference data:

- board definitions in `data/boards`
- initial game-state seeds in `data/seeds`

Clients submit complete `game_state` documents to each endpoint. The server archives the initial state returned by `GET /officiate` under `data/games/<game_id>/`, but it does not load from that archive for adjudication or officiation and does not maintain turn history, continuity validation, authorization state, or anti-cheat validation.

## Service Boundary

The application separates player-order resolution from automatic game administration.

### `adjudicate`

`adjudicate` evaluates player edits to a game state.

It accepts a pre-adjudicated state whose orders were authored or updated by players, resolves those orders for the current phase, and returns a judged state describing what happened. The judged state preserves the phase's submitted orders and records adjudication outcomes such as statuses, comments, dislodgements, and standoffs.

Conceptually:

`Given these submitted orders, what is the ruling?`

Current phase support:

- move adjudication resolves `hold`, `move`, `support`, and `convoy`
- retreat adjudication resolves `retreat`, `disband`, and survivor `hold` orders
- adjustment adjudication resolves `build`, `disband`, and `hold`

Adjustment build and disband orders are validated before any excessive-build, excessive-disband, or civil-disorder selection is applied. Players are not required to use every allowed build. Civil-disorder and excess-order selection prefer units or candidate builds furthest from a home supply center, then fleets before armies, then province name alphabetically.

### `officiate`

`officiate` advances the game without player involvement.

It accepts the verbatim judged result from `adjudicate` and performs the automatic bookkeeping needed to create the next player-editable state. Depending on phase, that bookkeeping may include relocating units, carrying or clearing dislodgements, resetting or creating orders for the next phase, updating phase/season/year, stamping `phase.started`, updating supply-center ownership, and computing build or disband obligations.

Conceptually:

`Given that ruling, what state do players edit next?`

This boundary should remain stable as new phase support is added. Phase-specific adjudication belongs in `adjudicate`, and phase-transition orchestration belongs in `officiate`, rather than mixing those responsibilities.

## Endpoint Contract

### `GET /officiate`

Returns the initial standard board game state as YAML.

The seed lives at:

`data/seeds/standard/1.yaml`

This location mirrors the board catalog path `data/boards/standard/1.yaml`. The seed is runtime data, not a regression fixture or example game instance. It is a template; `GET /officiate` overlays request-specific game options and stamps `phase.started`.

Supported query parameters are optional:

- `game_id`, defaulting to a random UUID
- `move_phase_minutes`, defaulting to `15`
- `retreat_phase_minutes`, defaulting to `5`
- `adjustment_phase_minutes`, defaulting to `5`

Phase-duration values must be positive integers. These values are returned as top-level `options` on the game state:

```yaml
options:
  game_id: "..."
  move_phase_minutes: 15
  retreat_phase_minutes: 5
  adjustment_phase_minutes: 5
```

The initial state is:

- `kind: game_state`
- `schema: 1`
- `revision: 1`
- `board: standard`
- `board_revision: 1`
- `phase.season: spring`
- `phase.step: move`
- `phase.year: 1901`
- `phase.started: <current epoch seconds>`
- top-level `options` populated from query parameters or defaults
- every starting unit has a pending hold order
- `standoffs: []`

After constructing that response state, `GET /officiate` creates a game archive directory:

`data/games/<game_id>/`

The archive root defaults to `data/games` and may be overridden with the `diplomacy.games.root` property.

It writes the exact returned initial game state as YAML using the phase-derived filename:

`<year>_<season>_<step>.yaml`

For the standard initial state with `game_id=demo`, the archive file is:

`data/games/demo/1901_spring_move.yaml`

The `game_id` must be a single path segment so request values cannot escape the games archive root.

### `POST /officiate`

Accepts an adjudicated `game_state` as YAML and returns the pre-adjudicated state for the next turn step.

Every `POST /officiate` response stamps the returned phase with `phase.started` equal to the current epoch seconds. Top-level `options` are copied from the submitted adjudicated state without modification.

For adjudicated move states, this means:

- successful move orders relocate their units
- dislodged units remain present with `dislodged_by`
- dislodged units receive pending `disband` orders by default, which clients may change to valid retreat orders
- non-dislodged units do not receive player-actionable retreat orders
- move-order fields and failure comments are removed
- top-level standoffs come from the adjudication outcome
- the returned phase has `step: retreat`

For adjudicated retreat states, this means:

- successful retreat orders relocate their units
- failed retreat orders remove the dislodged unit
- disbanded units are removed
- surviving units receive pending `hold` orders for the next player-editable state
- `dislodged_by`, retreat-order fields, failure comments, and standoffs are cleared
- spring retreats advance to `season: fall`, `step: move`
- fall retreats advance to `season: fall`, `step: adjustment`
- fall retreats update supply-center control after retreat cleanup; occupied supply centers transfer to the occupying unit's nation, and unoccupied supply centers keep their prior controller

For adjudicated adjustment states, this means:

- successful build orders become units with pending `hold` orders
- failed build orders are omitted because they were never existing units
- successful disband orders remove their units
- units removed by civil disorder are removed
- failed disband orders and other surviving existing units receive pending `hold` orders
- controls are preserved unchanged
- standoffs are cleared
- fall adjustments advance to `season: spring`, `step: move`, and increment the year

This endpoint uses the existing adjudication result semantics and `NextStateBuilder` behavior. It does not introduce build/disband adjudication or victory detection.

`POST /adjudicate` preserves submitted `phase.started` and top-level `options` unchanged. Adjudication judges the submitted phase; it does not start a new phase.

## Design Choices

Use one resource name with HTTP method semantics:

- `GET /officiate` starts a new game from the canonical seed template and game-option query parameters
- `POST /officiate` advances an adjudicated state into the next pre-adjudicated state

This avoids wrapper request types and keeps the YAML response shape as a plain `GameStateDocument`.

The initial-state archive is a side effect of `GET /officiate`, not a source of truth for later endpoint calls. `POST /adjudicate` and `POST /officiate` continue to evaluate the full state supplied in the request body.

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

All regression harnesses are REST-based. Adjudicate corpora call live `POST /adjudicate`, and officiate corpora call live `POST /officiate`, both through `YamlAdjudicationHttpClient`.

Move-to-retreat officiation fixtures should be generated from adjudicated move cases that contain at least one `dislodged_by`, because no-dislodgement move turns will later advance directly past retreat when phase progression is implemented. Retreat officiation fixtures should cover successful retreat relocation, failed retreat/disband removal, spring retreat phase advancement, and fall retreat supply-center control updates. Adjustment officiation fixtures should use the adjudicate adjustment `*_expected.yaml` files as `*_requested.yaml` inputs, then assert the next spring move state after applying successful builds, successful disbands, and civil-disorder removals.

Add focused HTTP tests that prove:

- `GET /officiate` returns the seed template with options and current `phase.started` applied
- `GET /officiate` accepts optional game-option query parameters and rejects non-positive phase durations
- `GET /officiate` archives the returned initial state at `data/games/<game_id>/<year>_<season>_<step>.yaml`
- `POST /officiate` accepts an adjudicated move-phase state and returns the same next-state shape currently produced by `NextStateBuilder`
- `POST /officiate` accepts adjudicated retreat states and returns the next pre-adjudicated move or adjustment state
- `POST /officiate` accepts adjudicated adjustment states and returns the next spring move state
- `POST /adjudicate` and `POST /officiate` preserve top-level `options`
- existing `POST /adjudicate` behavior remains unchanged

Run the focused web tests, the adjudicate move, retreat, and adjustment regression corpora, and the officiate move, retreat, and adjustment regression corpora after implementation.
