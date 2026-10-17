package com.jhaseft.tss.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "column_name", length = 500, nullable = false)
    private String columnName;

    @Column(length = 1000, nullable = false)
    private String text;

    @Column(length = 255)
    private String section;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private QuestionType type;

    @Column(name = "is_personal_data", nullable = false)
    private boolean personalData = false;

    @Column(name = "inference_confidence", precision = 3, scale = 2)
    private BigDecimal inferenceConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id")
    private Question parentQuestion;

    @Column(name = "conditional_value", length = 255)
    private String conditionalValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "likert_scale_id")
    private LikertScale likertScale;

    @Column(name = "matrix_group", length = 500)
    private String matrixGroup;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Survey getSurvey() { return survey; }
    public void setSurvey(Survey survey) { this.survey = survey; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public QuestionType getType() { return type; }
    public void setType(QuestionType type) { this.type = type; }

    public boolean isPersonalData() { return personalData; }
    public void setPersonalData(boolean personalData) { this.personalData = personalData; }

    public BigDecimal getInferenceConfidence() { return inferenceConfidence; }
    public void setInferenceConfidence(BigDecimal inferenceConfidence) { this.inferenceConfidence = inferenceConfidence; }

    public Question getParentQuestion() { return parentQuestion; }
    public void setParentQuestion(Question parentQuestion) { this.parentQuestion = parentQuestion; }

    public String getConditionalValue() { return conditionalValue; }
    public void setConditionalValue(String conditionalValue) { this.conditionalValue = conditionalValue; }

    public LikertScale getLikertScale() { return likertScale; }
    public void setLikertScale(LikertScale likertScale) { this.likertScale = likertScale; }

    public String getMatrixGroup() { return matrixGroup; }
    public void setMatrixGroup(String matrixGroup) { this.matrixGroup = matrixGroup; }
}
