package com.voice.agent.agent;

public final class AgentConstants {
    public static final String INTENT_CREATE_EVENT = "CREATE_EVENT";
    public static final String INTENT_QUERY_EVENT = "QUERY_EVENT";
    public static final String INTENT_UNKNOWN = "UNKNOWN";

    public static final String ACTION_CREATE_EVENT = "CREATE_EVENT";
    public static final String ACTION_QUERY_EVENT = "QUERY_EVENT";

    public static final String TARGET_CALENDAR_AGENT = "CalendarAgent";

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_WAITING_CONFIRM = "WAITING_CONFIRM";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private AgentConstants() {
    }
}
