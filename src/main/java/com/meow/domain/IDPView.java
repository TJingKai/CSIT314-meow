package com.meow.domain;

/** A timestamped visit to a published IDP; detail GETs alone do not create this entity. */
public record IDPView(String id, String viewerId, String projectId, String viewedAt) {}
