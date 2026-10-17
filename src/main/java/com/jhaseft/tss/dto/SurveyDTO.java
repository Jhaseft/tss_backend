package com.jhaseft.tss.dto;

import java.time.LocalDateTime;

public class SurveyDTO {

    private Long id;
    private String name;
    private String origin;
    private String publicSlug;
    private LocalDateTime createdAt;
    private long responseCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getPublicSlug() { return publicSlug; }
    public void setPublicSlug(String publicSlug) { this.publicSlug = publicSlug; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public long getResponseCount() { return responseCount; }
    public void setResponseCount(long responseCount) { this.responseCount = responseCount; }
}
