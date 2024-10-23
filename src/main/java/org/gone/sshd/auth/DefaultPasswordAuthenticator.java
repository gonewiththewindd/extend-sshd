package org.gone.sshd.auth;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DefaultPasswordAuthenticator implements PasswordAuthenticator {

    private Map<String, String> users = new HashMap<>() {{
        put("admin", "admin");
    }};

    @Override
    public boolean authenticate(String username, String password, ServerSession session) throws PasswordChangeRequiredException, AsyncAuthException {
        return StringUtils.equalsIgnoreCase(users.get(username), password);
    }
}
