package com.jhaseft.tss.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "question_options")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(length = 500, nullable = false)
    private String value;

    @Column(name = "display_text", length = 500, nullable = false)
    private String displayText;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "numeric_value", precision = 5, scale = 2)
    private BigDecimal numericValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public BigDecimal getNumericValue() { return numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }
}
