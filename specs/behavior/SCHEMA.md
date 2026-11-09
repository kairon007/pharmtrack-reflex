# Behavior journey grammar — `reflex/behavior/1`

This is the frozen contract. Swift Core and Kotlin core are two independent implementations
of it. Neither may import the other; the only thing they share is this directory.

A journey is a YAML file with `schema: reflex/behavior/1`. It contains **cases**, each case
is a `given` block plus an ordered list of `steps`. Steps mutate; `expect` blocks assert.
There is no other control flow — no loops, no conditionals, no expressions. If a journey
needs branching, write two cases.

```yaml
schema: reflex/behavior/1
id: vital_crud                 # filename stem, unique across the suite
title: Add, edit and delete a vital
nodes: [vitals, add_vital]     # machines this journey exercises; the runner emits their state
evidence:                      # where the legacy behavior came from
  - Sources/SharedUI/Medical/AddVitalView.swift:195-202
decisions: [D4, D10]           # deliberate divergences this journey asserts
unpinned:                      # what the runner must NOT compare for this journey
  - "rendered date strings (D15)"

cases:
  - id: add_weight
    given:
      fixture: empty
      key_value: { hasCompletedOnboarding: true }
    steps:
      - send: add_vital.setType
        args: { type: weight }
      - send: add_vital.setValueText
        args: { text: "76.2" }
      - expect:
          - path: state.add_vital.canSave
            equals: true
      - send: add_vital.save
      - flush: all
      - expect:
          - path: data.vitals.count
            equals: 1
```

## `given`

| Key | Meaning |
|---|---|
| `fixture` | A name from `fixtures/index.yaml`. Loads that file into the MedicalStore fixture. |
| `store` | `absent` (load returns nil), `unreadable` (load throws), or omit to use `fixture`. Mutually exclusive with `fixture`. |
| `key_value` | Map seeded into the KeyValueStore fixture. Defaults to `determinism.yaml#key_value`. |
| `now` | Override the pinned clock instant. ISO8601 UTC. Defaults to `determinism.yaml#clock.now`. |
| `ids` | Override the ID sequence. Defaults to `determinism.yaml#ids.sequence`. |
| `settings` | Partial override merged over the fixture's `notificationSettings`. |
| `data` | Inline `MedicalData` fragment merged over the fixture. **Top-level keys replace wholesale** — supplying `medications` replaces the whole array, it does not append. Used for cases needing one entity the fixtures do not contain (e.g. a legacy `schedule` string outside the enum). |
| `load` | Default `true`. See below. |

Every case starts from a fresh service graph. Cases never share state.

### Setup and the counter baseline

With `load: true` (the default) the runner seeds the services, then performs
`app_root.onAppear` and flushes to fixed point **before step 0**. The app is therefore
already loaded when the first step runs, which is what most journeys want.

With `load: false` the runner only seeds the services. The journey drives the load lifecycle
itself. `app_load` and `onboarding_journey` set this, because the lifecycle IS their subject.

`store.writeCount`, `notifications.replaceCount` and `ids.consumed` are all **zeroed at the
end of setup**. They count activity caused by the journey's steps, never by setup. So a
journey that saves once asserts `writeCount: 1` regardless of whether setup loaded a file,
and the notification set planned during setup is visible in `notifications` even though
`replaceCount` reads 0.

A journey may declare `defaults:` at file level to apply `given` keys to every case; a case's
own `given` overrides them.

## Steps

Exactly one verb per step, plus an optional `expect`.

| Verb | Form | Effect |
|---|---|---|
| `send` | `send: <node>.<action>` + optional `args` map | Dispatch an action to a machine. |
| `deliver` | `deliver: <node>.<message>` + optional `args` map | Deliver an asynchronous message directly, bypassing the effect that would normally produce it. Used to test message guards (e.g. a stale highlight clear) deterministically. |
| `flush` | `flush: scheduler \| store \| all` | Drain pending effects. `scheduler` fires every pending `after()` immediately; `store` completes pending async reads/writes; `all` does both to fixed point. |
| `advance` | `advance: { seconds: <n> }` | Move the pinned clock. Does not itself fire scheduler entries. |
| `inject` | `inject: { service: store, fail_next: <error> }` | Arm a fixture failure. |
| `note` | `note: <text>` | No-op. Explains a non-obvious step to a human reader. |

Actions and their argument names are defined in `contracts/nodes.yaml`. An action that does
not exist there is a validation error, not a runtime one.

### Effects are never implicit

Sending an action does **not** persist, does not fire timers, and does not reschedule
notifications. Those are effects. A journey must `flush` before asserting their results.
This is what makes the suite deterministic: the same steps produce the same state on both
platforms regardless of how each language schedules async work.

## Assertions

Each entry in `expect` has a `path` and exactly one operator.

### Path namespaces

| Prefix | Resolves to |
|---|---|
| `state.<node>.<field>` | A machine's State field. Names from `contracts/nodes.yaml`. |
| `data.<path>` | The committed `MedicalData` held by `app_root`. |
| `store.saved` | The last value written to the MedicalStore fixture, or `null`. |
| `store.writeCount` | Number of writes since setup completed. |
| `store.filePresent` | Whether the fixture slot holds a file. |
| `kv.<key>` | KeyValueStore fixture value. |
| `notifications` | The last `replaceAll` argument: the complete planned set. |
| `notifications.replaceCount` | How many times `replaceAll` was invoked since setup completed. |
| `clock.now` | The pinned instant. |
| `ids.consumed` | How many IDs the generator has handed out. |

Collection paths support `.count`, `[<n>]` indexing, and `[id=<uuid>]` selection by entity id.
Scalar entity fields are addressed directly: `data.medications[id=...].stock`.

A `derived` field in `nodes.yaml` that is a *predicate over entities* (`isTaken`, `isLowStock`)
is addressed by applying it to one entity: `state.medication_tracker.isTaken[$seed_medication_1]`.

### Value aliases

| Alias | Resolves to |
|---|---|
| `$seed_medication_1`, `$seed_vital_1`, ... | A reserved UUID from `determinism.yaml#ids.reserved`. |
| `$1`, `$2`, ... | The nth UUID handed out by the generator during this case. |
| `$fixture:<name>` | The parsed contents of `fixtures/<name>.json`, for whole-value equality. |
| `$fixture:<name>.<path>` | One field of that fixture, e.g. `$fixture:bp_composite.vitals[3].id`. Usable as a path suffix inside `[id=...]` too. |
| `$now` | The case's pinned clock instant at the moment the assertion runs. |

Aliases are resolved by the runner before comparison, identically on both platforms.

### Operators

| Operator | Applies to | Meaning |
|---|---|---|
| `equals` | any | Deep equality. Instants compare as instants, not strings. |
| `not_equals` | any | Inverse of `equals`. |
| `count` | collection | Element count. |
| `contains` | collection | Membership (deep equality for objects). |
| `not_contains` | collection | Non-membership. |
| `unordered_equals` | collection | Set equality — **required** for `notifications`, whose emission order is not contract. |
| `is_null` / `not_null` | any | Presence. Value is `true`. |
| `one_of` | scalar | Membership in the supplied list. Use only where D16 genuinely leaves a choice. |
| `matches` | string | Anchored regex. Never use on a rendered date (D15). |
| `gt` / `gte` / `lt` / `lte` | number, instant | Comparison. |
| `close_to` | number | `{ value: <n>, tolerance: <n> }`. For percentages and fractions only. |

## What is NOT asserted

- **Wall-clock durations.** D2. `advance` exists to move `now` for date math, never to time an animation.
- **Rendered date/time strings.** D15. State holds instants.
- **Colors, spacing, fonts, icon pixel shape.** Phase 4 device parity pass. State may hold a
  *semantic* icon name from `contracts/icons.yaml`, and that IS asserted.
- **Notification emission order.** Use `unordered_equals`.
- **Anything in the journey's `unpinned` list.** The runner excludes those paths from the diff.

## Divergence markers

A case that asserts behavior different from v0.0.19 MUST carry `divergence: <decision id>`.
The validator rejects a case citing a decision in its `divergence` that is not in the file's
`decisions` list. This keeps every intentional change traceable to a signed-off decision
instead of burying it in an assertion.

## Runner

See `contracts/runner.yaml`. In short: `reflex-runner --platform ios|android --spec <glob>`
emits one JSON object per step to stdout as NDJSON; CI diffs the two streams byte-for-byte
after canonicalization. A journey passes when both platforms produce identical streams and
every `expect` holds.