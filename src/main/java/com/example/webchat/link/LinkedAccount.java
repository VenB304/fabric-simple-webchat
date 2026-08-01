package com.example.webchat.link;

/** A web session verified as belonging to a specific in-game account. */
public record LinkedAccount(String uuid, String username) {
}
