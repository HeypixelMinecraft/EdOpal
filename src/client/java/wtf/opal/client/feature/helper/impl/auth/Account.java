package wtf.opal.client.feature.helper.impl.auth;

/**
 * 账号数据模型（基于 ksyzov/AccountManager 的 Account）
 */
public final class Account {

    private String refreshToken;
    private String accessToken;
    private String username;
    private String uuid;
    private long unban;

    public Account(String refreshToken, String accessToken, String username) {
        this(refreshToken, accessToken, username, 0L);
    }

    public Account(String refreshToken, String accessToken, String username, long unban) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.username = username;
        this.unban = unban;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public long getUnban() {
        return unban;
    }

    public void setUnban(long unban) {
        this.unban = unban;
    }

    public boolean isMicrosoft() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return username != null && username.equals(account.username);
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}
