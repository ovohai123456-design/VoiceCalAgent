package com.voice.agent.agent;

public final class ConversationConstants {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    public static final String STATE_CLARIFY = "CLARIFY";
    public static final String STATE_CONFIRM = "CONFIRM";
    public static final String STATE_EVENT_SELECTION = "EVENT_SELECTION";
    public static final String STATE_SLOT_SELECTION = "SLOT_SELECTION";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    private ConversationConstants() {
    }
}
