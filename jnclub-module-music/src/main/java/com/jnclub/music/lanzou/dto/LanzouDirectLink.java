package com.jnclub.music.lanzou.dto;

import java.time.Instant;

public record LanzouDirectLink(String url, Instant expiresAt) {}
