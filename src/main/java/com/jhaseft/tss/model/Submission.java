package com.jhaseft.tss.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "row_index")
    private Integer rowIndex;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private SubmissionSource source = SubmissionSource.DIRECT;

    @Column(name = "is_inconsistent", nullable = false)
    private boolean inconsistent = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Survey getSurvey() { return survey; }
    public void setSurvey(Survey survey) { this.survey = survey; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }

    public SubmissionSource getSource() { return source; }
    public void setSource(SubmissionSource source) { this.source = source; }

    public boolean isInconsistent() { return inconsistent; }
    public void setInconsistent(boolean inconsistent) { this.inconsistent = inconsistent; }
}
