# Infinite Canvas Operation Apply Optimization Before / After

## Summary

This document covers a second-stage Infinite Canvas performance optimization.

- Previous optimization: active-room lookup and WebSocket participant-event payload
- This optimization: element application inside `applyOperations()`

The previous charts explain Redis active-room lookup and WebSocket payload size. The new charts explain how long the server takes to apply create, update, and delete operations to the `elements` list. These are intentionally documented as separate before/after stories.

<br>

## Optimization Target

The Infinite Canvas edit flow works as follows.

```text
Client sends operations
-> Server loads the current room state
-> Server applies operations to elements
-> Server stores the updated state in Redis
-> Server publishes the change event to other participants
```

This optimization only targets the `apply operations to elements` step.

Related code:

- `InfiniteCanvasServiceImpl.applyOperations(...)`
- `InfiniteCanvasServiceImpl.applyOperation(...)`
- `InfiniteCanvasServiceImpl.CanvasElementBatch`

<br>

## Before: List-Based Element Updates

The previous implementation scanned the whole element list for each operation.

```text
UPSERT / UPDATE
-> elements.removeIf(elementId matches)
-> elements.add(updatedElement)

DELETE
-> elements.removeIf(elementId matches)
```

This was simple, but the cost grew quickly when both element count and operation count increased.

```text
Estimated lookup work ~= elementCount * operationCount
```

For example, `5000 elements + 100 operations` could perform roughly `500,000` element comparisons in a single message.

<br>

## After: Map-Based Batch Updates

The optimized implementation builds an index from the current element list once, then applies operations against that index.

```text
Scan initial elements once
-> Build elementId -> slotKey index
-> Apply remove/upsert operations through the index
-> Rebuild the final elements list
```

The expected work changes to:

```text
Estimated lookup work ~= elementCount + operationCount
```

The existing behavior is preserved.

- `UPDATE` / `UPSERT` removes existing elements with the same `elementId`, then appends the new element.
- `DELETE` removes elements with the same `elementId`.
- `CLEAR_CANVAS` clears both elements and locks.
- Anonymous elements without an `id` keep their existing order.
- If duplicated `elementId` values already exist, all matching elements are removed, matching the old `removeIf` behavior.

<br>

## Benchmark Method

The benchmark is synthetic. It excludes Redis, network, and WebSocket broadcast costs, and compares only the pure in-memory cost of applying operations to `elements`.

```bash
python3 backend/scripts/benchmark-infinite-canvas-performance.py --iterations 20 --output-dir backend/docs/performance/assets
```

Scenarios:

- `1000 elements + 10 operations`
- `3000 elements + 50 operations`
- `5000 elements + 100 operations`

Metrics:

- Average processing time
- p95 processing time
- Estimated element lookup work

<br>

## Benchmark Results

| Elements | Operations | Before avg ms | Before p95 ms | After avg ms | After p95 ms | p95 improvement | Before estimated lookups | After estimated lookups |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,000 | 10 | 0.56 | 0.74 | 0.51 | 0.82 | 0.90x | 10,000 | 1,010 |
| 3,000 | 50 | 8.36 | 10.68 | 2.18 | 2.73 | 3.91x | 150,000 | 3,050 |
| 5,000 | 100 | 41.34 | 54.58 | 3.79 | 4.39 | 12.43x | 500,000 | 5,100 |

![Operation apply p95 latency](./assets/infinite-canvas-operation-apply-p95.svg)

![Element lookup work per message](./assets/infinite-canvas-operation-lookup-steps.svg)

<br>

## Interpretation

For small canvases, the fixed cost of building the Map index can offset the benefit. In the `1000 elements + 10 operations` scenario, p95 measured `0.74ms -> 0.82ms`, so this is not a meaningful user-facing improvement for small rooms.

The benefit becomes clear as the canvas and operation batch grow.

- `3000 elements + 50 operations`: p95 `10.68ms -> 2.73ms`, about `3.91x` faster
- `5000 elements + 100 operations`: p95 `54.58ms -> 4.39ms`, about `12.43x` faster

This optimization is therefore best described as a large-canvas tail-latency improvement. It prevents server-side operation application from growing with `elementCount * operationCount`.

<br>

## User-Visible Impact

This does not directly improve browser FPS. It improves how quickly the server applies edit operations.

Expected effects:

- Lower server-side delay when moving, updating, or deleting shapes in large canvases
- Less waiting when many operations arrive in one message
- Lower chance that collaboration updates feel delayed
- Better p95/p99 tail latency under heavier canvas states

Rendering smoothness still depends on frontend rendering, the canvas engine, network conditions, and WebSocket receive handling. The accurate claim is “lower server apply latency,” not “higher client FPS.”

<br>

## Relationship To Previous Charts

This does not replace the previous before/after graphs.

| Document | Before | After | Scope |
| --- | --- | --- | --- |
| Previous Redis/WebSocket optimization | Redis SCAN, full state payload | Sorted Set, delta payload | Lookup and event payload |
| This operation optimization | List-based element updates | Map-based batch element updates | Edit-operation application |

For presentations and portfolio material, keep these as two separate optimization stories.

<br>

## Verification

The Infinite Canvas integration test passed after the logic change.

```bash
./gradlew --no-daemon test --tests com.nemonicworld.infinitecanvas.controller.InfiniteCanvasControllerIntegrationTest
```

Result: `BUILD SUCCESSFUL`
