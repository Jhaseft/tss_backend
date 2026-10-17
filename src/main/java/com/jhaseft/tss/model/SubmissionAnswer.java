package com.jhaseft.tss.model;

import jakarta.persistence.*;

@Entity
@Table(name = "submission_answers")
public class SubmissionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    @Column(name = "normalized_value", columnDefinition = "TEXT")
    private String normalizedValue;

    @Column(name = "is_not_applicable", nullable = false)
    private boolean notApplicable = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }

    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }

    public boolean isNotApplicable() { return notApplicable; }
    public void setNotApplicable(boolean notApplicable) { this.notApplicable = notApplicable; }
}
