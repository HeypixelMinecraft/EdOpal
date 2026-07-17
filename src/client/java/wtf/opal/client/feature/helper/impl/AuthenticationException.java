package wtf.opal.client.feature.helper.impl;

/**
 * 自定义认证异常类，用于区分不同类型的认证错误
 */
public class AuthenticationException extends Exception {

    private final AuthenticationError errorType;

    public AuthenticationException(AuthenticationError errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public AuthenticationException(AuthenticationError errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public AuthenticationError getErrorType() {
        return errorType;
    }

    /**
     * 认证错误类型枚举
     */
    public enum AuthenticationError {
        /**
         * 网络连接失败
         */
        NETWORK_ERROR("Network connection failed"),

        /**
         * 用户取消认证
         */
        CANCELLED("Authentication cancelled by user"),

        /**
         * Token 过期或无效
         */
        INVALID_TOKEN("Invalid or expired token"),

        /**
         * Microsoft 认证失败
         */
        MICROSOFT_AUTH_FAILED("Microsoft authentication failed"),

        /**
         * Xbox Live 认证失败
         */
        XBOX_AUTH_FAILED("Xbox Live authentication failed"),

        /**
         * Minecraft 认证失败
         */
        MINECRAFT_AUTH_FAILED("Minecraft authentication failed"),

        /**
         * 浏览器打开失败
         */
        BROWSER_ERROR("Failed to open browser"),

        /**
         * 账号信息保存失败
         */
        STORAGE_ERROR("Failed to save account data"),

        /**
         * 未知错误
         */
        UNKNOWN("Unknown error");

        private final String message;

        AuthenticationError(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}