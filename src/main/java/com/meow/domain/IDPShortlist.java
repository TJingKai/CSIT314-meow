package com.meow.domain;

/** Historical shortlist event created only when a favourite is newly inserted. */
public record IDPShortlist(String id, String customerId, String projectId, String savedAt) {}
