package com.nemonicworld.infinitecanvas.dto.websocket;

public enum InfiniteCanvasEventType {
    /** Full canvas state snapshot. */
    STATE_SNAPSHOT,
    /** Full canvas snapshot was replaced. */
    SNAPSHOT_UPDATED,
    /** Canvas edit operations were accepted. */
    OPS_APPLIED,
    /** Participant cursor was updated. */
    CURSOR_UPDATED,
    /** Element lock was acquired. */
    LOCK_ACQUIRED,
    /** Element lock was released. */
    LOCK_RELEASED,
    /** A participant connected through WebSocket. */
    PARTICIPANT_CONNECTED,
    /** A participant disconnected from WebSocket. */
    PARTICIPANT_DISCONNECTED,
    /** A participant left the canvas. */
    PARTICIPANT_LEFT,
    /** Host role was transferred to another participant. */
    HOST_CHANGED,
    /** A participant profile changed. */
    PARTICIPANT_UPDATED,
    /** The canvas was closed. */
    CANVAS_CLOSED,
    /** A previous session was replaced by a new session. */
    DUPLICATE_SESSION_CLOSED,
    /** Heartbeat response. */
    PONG,
    /** Safe error message for the client. */
    ERROR
}
