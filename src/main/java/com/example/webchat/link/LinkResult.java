package com.example.webchat.link;

/** The outcome of successfully redeeming an in-game link code. */
public record LinkResult(String token, LinkedAccount account) {
}
