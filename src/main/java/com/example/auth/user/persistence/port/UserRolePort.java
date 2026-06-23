package com.example.auth.user.persistence.port;

import java.util.List;

public interface UserRolePort {

    void replaceRoles(Long userId, List<String> roles);
}
