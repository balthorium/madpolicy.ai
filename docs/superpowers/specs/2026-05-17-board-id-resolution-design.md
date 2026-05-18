# Board ID Resolution Design

## Summary

Board identity should be explicit in both board documents and game-state documents.
Boards will carry a required internal `id`, and game states will carry a required `board`
that identifies which board the state applies to.

Board lookup will be based exclusively on the internal board ID, not on a caller-supplied
path. The engine will scan `data/boards/*.yaml`, parse each board, and resolve the
requested board from that in-memory index.

## Goals

- Make board identity part of the domain model.
- Make game states explicitly identify the board they belong to.
- Resolve boards by internal ID rather than external file path.
- Enforce repository hygiene for board metadata by requiring filename and board ID to match.
- Keep move adjudication behavior unchanged aside from board lookup and validation.

## Non-Goals

- Backward compatibility with board or game-state YAML that omits the new fields.
- Multi-directory board discovery.
- Runtime board mutation or persistence.
- Changes to move-adjudication rules.

## Data Model

### Board Document

`BoardDocument` will gain a required `id` field.

YAML shape:

```yaml
kind: board
version: 1
id: standard
provinces:
  ...
adjacency:
  ...
```

### Game State Document

`GameStateDocument` will gain a required `board` field.

YAML shape:

```yaml
kind: game_state
version: 1
board: standard
phase:
  ...
nations:
  ...
standoffs: []
```

## Board Resolution

Board resolution will scan `data/boards` for YAML files, load every board document,
and build an in-memory `id -> board` map.

Resolution rules:

- The internal `id` is authoritative.
- A board file whose basename does not exactly match the internal `id` is invalid.
- Duplicate internal board IDs across files are invalid.
- Missing requested board ID is invalid.

These conditions will fail fast with specific error messages that name the ID and relevant
file paths.

## Service Flow

`AdjudicationService` should stop treating a board path as primary input.

Target flow:

1. Load the game state from the provided state path.
2. Read `game_state.board`.
3. Scan and index boards from `data/boards/*.yaml`.
4. Resolve the board by internal ID.
5. Build `BoardIndex` from the resolved board.
6. Adjudicate and build both `adjudicated_state` and `next_state`.

## Regression Harness

The regression harness should resolve boards the same way as production code instead of
using a hard-coded board path. This ensures fixtures validate the board linkage end to end.

Every regression fixture will therefore need an explicit `board`, and every board
fixture will need an explicit `id`.

## Testing Strategy

Add or update tests for:

- YAML parsing and round-trip of board `id`.
- YAML parsing and round-trip of game-state `board`.
- Successful resolution of a board by internal ID.
- Failure when a requested board ID does not exist.
- Failure when two board files share the same internal ID.
- Failure when a board filename does not match its internal ID.
- Existing adjudication and regression paths still passing with resolved boards.

## Risks

- The board scan introduces a new failure mode if stray or malformed board files are added.
- Tests that construct `BoardDocument` or `GameStateDocument` directly will need coordinated
  updates for the new required fields.
- Existing fixtures must be updated consistently or parsing will fail immediately.
