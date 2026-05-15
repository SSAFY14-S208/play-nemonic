package com.nemonicworld.infinitecanvas.dto.websocket;

public enum InfiniteCanvasEventType {
    /** Full canvas state snapshot. */
    STATE_SNAPSHOT,
    /** Full canvas snapshot was replaced. */
    SNAPSHOT_UPDATED,
    /** Canvas edit operations were accepted. */
    OPS_APPLIED,
    /** A participant connected through WebSocket. */
    PARTICIPANT_CONNECTED,
    /** A participant disconnected from WebSocket. */
    PARTICIPANT_DISCONNECTED,
    /** A participant left the canvas. */
    PARTICIPANT_LEFT,
    /** A participant profile changed. */
    PARTICIPANT_UPDATED,
    /** A previous session was replaced by a new session. */
    DUPLICATE_SESSION_CLOSED,
    /** Heartbeat response. */
    PONG,
    /** Safe error message for the client. */
    ERROR
}
