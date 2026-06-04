# Service Captures

MangoSleave Nemonic demo site screenshots for portfolio, presentation, and handoff materials.

Image and video files are committed with ASCII slugs to keep Git diffs and MR reviews readable across macOS and GitLab. Full-length videos live in the matching feature folders and are tracked with Git LFS because they are large.

## Top-Level Assets

| Asset | Notes |
| --- | --- |
| `system-overview.png` | Overall system structure diagram |

## Feature Folders

| Feature | Folder | Notes |
| --- | --- | --- |
| 3D 메인룸 | `3d-main-room/` | Monitor navigation and bottom-tab interaction videos |
| 네모닉 체험관 | `nemonic-experience/` | Printer entry, output, gallery output, phone modal flow |
| 무한캔버스 | `infinite-canvas/` | Lobby, room creation, drawing, multi-user canvas, AI sticker, screenshot flow |
| 백오피스 | `backoffice/` | Dashboard, statistics, AI prompt, OCR, community, CS inquiry, audit/admin screens |
| 오늘의 운세 | `daily-fortune/` | Entry screen |
| 우당탕 릴레이 드로잉 | `relay-drawing/` | Entry, lobby, QR invite, guide, game, result, room-close screens across devices |
| 커뮤니티보드 | `community-board/` | Board list, memo create/detail/share/print/report/delete, phone gallery flow |
| 플립북 | `flipbook/` | Entry, lobby, QR/link share, guide, canvas, result, phone modal flow |

## Naming Rule

Image files follow this pattern:

```text
NN_screen-or-flow-description.png
```

The numeric prefix preserves the demo flow order inside each feature folder.

Video files follow this pattern:

```text
video_description.mp4
video_description.mov
```
