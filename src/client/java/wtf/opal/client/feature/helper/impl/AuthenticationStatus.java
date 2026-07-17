package wtf.opal.client.feature.helper.impl;

/**
 * 认证状态枚举，用于跟踪认证流程的各个阶段
 */
public enum AuthenticationStatus {
    /**
     * 空闲状态，无认证操作
     */
    IDLE("Idle"),

    /**
     * 等待用户在浏览器中登录
     */
    WAITING_FOR_BROWSER("Waiting for browser login"),

    /**
     * 正在获取 Microsoft 授权码/访问令牌
     */
    ACQUIRING_TOKENS("Acquiring Microsoft tokens"),

    /**
     * 正在进行 Xbox Live 认证
     */
    AUTHENTICATING_XBOX("Authenticating with Xbox Live"),

    /**
     * 正在进行 Xbox XSTS 认证
     */
    AUTHENTICATING_XSTS("Authenticating with Xbox XSTS"),

    /**
     * 正在进行 Minecraft 认证
     */
    AUTHENTICATING_MINECRAFT("Authenticating with Minecraft"),

    /**
     * 正在获取 Minecraft 个人资料
     */
    FETCHING_PROFILE("Fetching Minecraft profile"),

    /**
     * 正在刷新令牌
     */
    REFRESHING_TOKEN("Refreshing token"),

    /**
     * 正在保存账号信息
     */
    SAVING_ACCOUNT("Saving account"),

    /**
     * 认证成功
     */
    SUCCESS("Authentication successful"),

    /**
     * 认证失败
     */
    FAILED("Authentication failed");

    private final String displayName;

    AuthenticationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 判断是否处于活跃状态（正在认证）
     */
    public boolean isActive() {
        return this != IDLE && this != SUCCESS && this != FAILED;
    }

    /**
     * 判断认证是否完成（成功或失败）
     */
    public boolean isCompleted() {
        return this == SUCCESS || this == FAILED;
    }
}
