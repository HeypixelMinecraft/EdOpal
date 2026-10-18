package wtf.opal.event.impl.game.server;

/**
 * 服务器断开连接事件，携带断开原因文本
 */
public final class ServerDisconnectEvent {

    private final String reason;

    public ServerDisconnectEvent() {
        this("");
    }

    public ServerDisconnectEvent(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
