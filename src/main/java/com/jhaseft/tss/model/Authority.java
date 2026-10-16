package com.jhaseft.tss.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "jhi_authority")
public class Authority {

    @Id
    @Column(length = 50)
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
