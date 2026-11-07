package com.meow.domain;

/** Favourite membership is uniquely keyed by customer and IDP in the database. */
public record Favourite(String customerId, String projectId, String createdAt) {}
