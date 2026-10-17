package com.jhaseft.tss.model;

import jakarta.persistence.*;

@Entity
@Table(name = "derived_variables")
public class DerivedVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(name = "output_type", length = 30, nullable = false)
    private String outputType;

    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Survey getSurvey() { return survey; }
    public void setSurvey(Survey survey) { this.survey = survey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOutputType() { return outputType; }
    public void setOutputType(String outputType) { this.outputType = outputType; }

    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }
}
