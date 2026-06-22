package com.yasirkhan.trip.models;

import java.security.Principal;

public record UserPrincipal(
        String userId,
        String username,
        String role
) implements Principal {

    @Override
    public String getName() {
        return this.username;
    }
}