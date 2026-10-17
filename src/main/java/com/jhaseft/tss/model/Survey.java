package com.jhaseft.tss.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "surveys")
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SurveyOrigin origin;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "public_slug", length = 36, nullable = false, unique = true)
    private String publicSlug;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public SurveyOrigin getOrigin() { return origin; }
    public void setOrigin(SurveyOrigin origin) { this.origin = origin; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public String getPublicSlug() { return publicSlug; }
    public void setPublicSlug(String publicSlug) { this.publicSlug = publicSlug; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
