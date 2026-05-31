package com.voice.agent.auth;

import javax.servlet.http.HttpServletRequest;

public final class AuthContext {
    public static final String USER_ID_ATTRIBUTE = AuthContext.class.getName() + ".userId";

    private AuthContext() {
    }

    public static Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
        if (!(userId instanceof Long)) {
            throw new IllegalStateException("登录状态无效，请重新登录");
        }
        return (Long) userId;
    }
}
