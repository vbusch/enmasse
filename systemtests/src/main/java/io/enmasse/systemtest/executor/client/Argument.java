package io.enmasse.systemtest.executor.client;

/**
 * Enum with argument for external clients
 */
public enum Argument {
    //connection opts
    CONN_URLS("--conn-urls"),
    CONN_RECONNECT("--conn-reconnect"),
    CONN_RECONNECT_INTERVAL("--conn-reconnect-interval"),
    CONN_RECONNECT_LIMIT("--conn-reconnect-limit"),
    CONN_RECONNECT_TIMEOUT("--conn-reconnect-timeout"),
    CONN_HEARTBEAT("--conn-heartbeat"),
    CONN_SSL("--conn-ssl"),
    CONN_SSL_CERTIFICATE("--conn-ssl-certificate"),
    CONN_SSL_PRIVATE_KEY("--conn-ssl-private-key"),
    CONN_SSL_PASSWORD("--conn-ssl-password"),
    CONN_SSL_TRUST_STORE("--conn-ssl-trust-store"),
    CONN_SSL_VERIFY_PEER("--conn-ssl-verify-peer"),
    CONN_SSL_VERIFY_PEER_NAME("--conn-ssl-verify-peer-name"),
    CONN_MAX_FRAME_SIZE("--conn-max-frame-size"),
    CONN_WEB_SOCKET("--conn-web-socket"),
    CONN_OPTIONS("--connection-options"),

    //common link opts
    LINK_DURABLE("--link-durable"),
    LINK_AT_MOST_ONCE("--link-at-most-once"),
    LINK_AT_LEAST_ONCE("--link-at-least-once"),
    CAPACITY("--capacity"),

    //logging opts
    LOG_LIB("--log-lib"),
    LOG_STATS("--log-stats"),
    LOG_MESSAGES("--log-msgs"),

    //common opts
    BROKER("--broker"),
    BROKER_URL("--broker-url"),
    USERNAME("--conn-username"),
    PASSWORD("--conn-password"),
    ADDRESS("--address"),
    COUNT("--count"),
    CLOSE_SLEEP("--close-sleep"),
    TIMEOUT("--timeout"),
    DURATION("--duration"),

    //sender opts
    MSG_ID("--msg-id"),
    MSG_GROUP_ID("--msg-group-id"),
    MSG_GROUP_SEQ("--msg-group-seq"),
    MSG_REPLY_TO_GROUP_ID("--msg-reply-to-group-id"),
    MSG_SUBJECT("--msg-subject"),
    MSG_REPLY_TO("--msg-reply-to"),
    MSG_PROPERTY("--msg-property"),
    MSG_DURABLE("--msg-durable"),
    MSG_TTL("--msg-ttl"),
    MSG_PRIORITY("--msg-priority"),
    MSG_CORRELATION_ID("--msg-correlation-id"),
    MSG_USER_ID("--msg-user-id"),
    MSG_CONTENT_TYPE("--msg-content-type"),
    MSG_CONTENT("--msg-content"),
    MSG_CONTENT_LIST_ITEM("--msg-content-list-item"),
    MSG_CONTENT_MAP_ITEM("--msg-content-map-item"),
    MSG_CONTENT_FROM_FILE("--msg-content-from-file"),
    MSG_ANNOTATION("--msg-annotation"),
    ANONYMOUS("--anonymous"),

    //receiver opts
    SELECTOR("--msg-selector"),
    RECV_BROWSE("--recv-browse"),
    ACTION("--action"),
    PROCESS_REPLY_TO("--process-reply-to"),
    RECV_LISTEN("--recv-listen"),
    RECV_LISTEN_PORT("--recv-listen-port")
    ;

    private String command;

    Argument(String command) {
        this.command = command;
    }

    /**
     * Gets command for external client
     * @return string command
     */
    public String command() {
        return command;
    }
}
