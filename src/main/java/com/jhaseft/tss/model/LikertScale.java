package com.jhaseft.tss.model;

import jakarta.persistence.*;

@Entity
@Table(name = "likert_scales")
public class LikertScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
}
