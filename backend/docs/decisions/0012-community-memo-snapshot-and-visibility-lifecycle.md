# 0012. Community Memo Snapshot And Visibility Lifecycle

Date: 2026-05-10

## Status

Accepted

## Context

Community canvas memos can start from several frontend flows:

- a blank direct drawing/writing canvas
- an existing gallery artifact imported for editing
- a photo or other client-side composition

The backend should not treat a community memo as a live view over the original
gallery artifact or uploaded source. The community wall needs a stable image
that represents exactly what the user posted at attach time, while still keeping
enough metadata for ownership, moderation, reporting, and admin review.

The wall also needs consistent visibility rules across user APIs, admin APIs,
FIFO cleanup, reporting, and manual admin review.

## Decision

Store community memos as final rendered image snapshots.

For every created community memo:

- `community_memo.body_image_url` stores the final original-size community
  display snapshot object key.
- `community_memo.thumbnail_image_url` stores the final community display
  thumbnail snapshot object key.
- `community_memo.artifact_id` is nullable source metadata.
- `artifact_id = null` means `DIRECT`.
- `artifact_id != null` means `GALLERY`.
- Even for `GALLERY` memos, wall rendering uses the community snapshot URLs, not
  artifact subtype image URLs.

At memo creation time, the backend preserves the confirmed `COMMUNITY`
`file_upload` source objects and creates separate transparent PNG derivatives
for wall display:

- Body derivative: `community/memos/{memoId}/body.png`.
- Thumbnail derivative: `community/memos/{memoId}/thumbnail.png`.
- The derivative content type is `image/png`; JPEG derivatives are not used.
- The first pass does not use AI, GMS, or an external background-removal API.
- White-key processing scans every pixel and makes white-like pixels
  transparent when `r >= threshold && g >= threshold && b >= threshold`.
- The threshold is configured by
  `nemonic.community.memo.image.white-threshold`, default `245`.
- This is intentionally not flood-fill; white areas inside closed shapes are
  transparent too.
- Existing transparent pixels remain transparent, and non-white colors keep
  their color and alpha.

Return three image fields:

- `memoOriginalImageUrl`: public URL for `body_image_url`.
- `memoThumbnailImageUrl`: public URL for `thumbnail_image_url`.
- `memoImageUrl`: compatibility representative URL, using thumbnail first and
  original fallback.

Run moderation before inserting a memo.

- The moderation client receives the derivative original snapshot URL,
  derivative thumbnail URL, client text, and source type.
- Allowed results insert the memo with `moderation_status = allowed` when the
  database enum supports it.
- Blocked results do not insert a memo.
- Moderation errors and timeouts are fail-closed by default.
- OCR text, OCR categories, and moderation checked time are stored when
  available.
- If derivative objects were uploaded but moderation, database insert, or later
  create-time processing fails, the derivative objects are deleted
  best-effort. The original upload objects, artifact results, gallery rows, and
  file upload rows are not deleted by this flow.

Use visibility states instead of physical deletion for wall lifecycle:

- User delete is soft delete: `deleted_at`, `deleted_reason = user_delete`, and
  `updated_at`.
- FIFO cleanup is also soft delete: `deleted_reason = expired`.
- Report threshold hiding sets `is_hidden = true`,
  `hidden_reason = report_threshold`, and `hidden_at`.
- Admin manual hiding sets `is_hidden = true`,
  `hidden_reason = admin_hidden`, `hidden_at`, `reviewed_by`, and
  `reviewed_at`.
- Admin restore clears `is_hidden`, `hidden_reason`, and `hidden_at`, and records
  review metadata.

Visibility policy:

- Public list/detail/update/delete/report APIs only operate on visible memos:
  `deleted_at IS NULL AND is_hidden = false`.
- Hidden or deleted memos return the same not-found response to regular users.
- Admin community APIs can inspect hidden memos but exclude soft-deleted memos
  by default.
- Admin hide/restore does not run FIFO or moderation.
- User layout update/delete/report does not mutate images, artifact rows,
  gallery rows, file uploads, or moderation data.

Reporting policy:

- Reports are stored in `community_memo_report`.
- A user can report the same memo once.
- A user cannot report their own memo.
- `community_memo.report_count` is incremented transactionally.
- When `report_count` reaches the configured `community.report_hide_threshold`
  value, the memo is automatically hidden with
  `hidden_reason = report_threshold`. The default threshold is 5 reports.
- Admin detail embeds latest report history, including `reason_detail`, to
  support review decisions.

## Consequences

- Positive: Community rendering is stable even if the source artifact, gallery
  row, or file upload lifecycle changes later.
- Positive: Community wall images can render as transparent memo stickers while
  original uploads and artifact result images remain intact for gallery,
  download, card, or other product surfaces.
- Positive: Direct and gallery-based community memos share the same display
  image contract.
- Positive: Existing `memoImageUrl` clients keep working while newer clients can
  choose original or thumbnail explicitly.
- Positive: Admin review can distinguish AI/report/admin visibility decisions
  through `moderation_status`, `hidden_reason`, `reviewed_by`, and `reviewed_at`.
- Positive: User-facing APIs avoid leaking whether a memo was hidden or deleted.
- Negative: Each memo requires two confirmed upload files before creation.
- Negative: Gallery-origin memos duplicate rendered image storage instead of
  reusing artifact subtype images directly.
- Negative: White text, white highlights, and white decoration pixels are also
  transparent because white-key processing removes all white-like pixels.
- Negative: Complex photographic cutouts are outside the first-pass algorithm.
- Negative: Restoring a hidden memo can temporarily push visible memo count over
  the wall limit until the next create-time FIFO pass.
- No migration: existing `body_image_url` and `thumbnail_image_url` columns store
  derivative object keys, while existing `file_upload` rows keep source upload
  tracking.
- Follow-up: Evaluate AI segmentation or a dedicated background-removal model if
  the product needs reliable complex photo cutouts without removing intentional
  white foreground details.
- Follow-up: If admin review history needs full auditability, add a dedicated
  admin review/audit table instead of relying only on latest `reviewed_by` and
  `reviewed_at`.
- Follow-up: If report history grows beyond current page sizes, keep the
  dedicated admin report-history endpoint paginated and avoid embedding all
  reports in admin detail responses.
