# Click Scheme — FSRS-Based Interaction Model

MemoryPrime uses a **two-mode click system** for practicing notes. Clicks are counted in rapid succession — a pause between clicks finalizes the action.

## Default Mode

| Clicks | Rating | Description |
|--------|--------|-------------|
| 1 | **Good** | Normal recall. Proceeds to next note. |
| 2 | **Again** | Failed to recall. Note re-enters the queue. |
| 3 | **Easy** | Effortless recall. Longer interval before next review. |
| 4 | **Hard** | Difficult recall. Shorter interval / decreased easiness. |
| 5 | **Back** | Undo — go back to previous question ↔ answer. |
| 6 | **Secondary** | Enter secondary mode for destructive actions. |
| 7+ | Cancel | No action taken. |

**On questions:** Clicks 1–4 flip to the answer side. Ratings are only applied when viewing the answer.

### Grade Mapping (Note.java)

| FSRS Rating | Internal Grade | Easiness Effect |
|-------------|---------------|-----------------|
| Again | 1 | Drops to acquisition phase |
| Hard | 2 | Easiness −0.16 |
| Good | 4 | No easiness change |
| Easy | 5 | Easiness +0.10 |

## Secondary Mode

Entered via 6 clicks in Default mode. Used for destructive actions that require confirmation.

| Clicks | Action |
|--------|--------|
| 1 | **Pending Postpone** — requeue note to end of queue |
| 2 | **Pending Delete** — delete note from storage |
| 3–5 | No action |

### Confirmation Flow

After selecting a pending action, TTS announces "triple click to confirm":

| Clicks | Result |
|--------|--------|
| 1 | **Cancel** — return to Default mode, no action taken |
| 3 | **Confirm** — execute the pending action (rapid clicks within 300ms) |
| 2, 4, 5 | No action — stay in pending state |

After confirmation or cancellation, the system returns to Default mode.

## State Machine

```
Default Mode
  ├── 1 click → Good (proceed)
  ├── 2 clicks → Again (failed)
  ├── 3 clicks → Easy (easy recall)
  ├── 4 clicks → Hard (difficult)
  ├── 5 clicks → Back/Undo
  └── 6 clicks → Secondary Mode
                    ├── 1 click → Pending Postpone
                    │               ├── 1 click → Cancel → Default
                    │               └── 3 clicks → Confirm → Default
                    └── 2 clicks → Pending Delete
                                    ├── 1 click → Cancel → Default
                                    └── 3 clicks → Confirm → Default
```
