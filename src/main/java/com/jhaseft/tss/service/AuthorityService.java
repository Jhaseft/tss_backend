package com.jhaseft.tss.service;

import com.jhaseft.tss.model.Authority;
import com.jhaseft.tss.repository.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuthorityService {

    private final AuthorityRepository authorityRepository;

    public AuthorityService(AuthorityRepository authorityRepository) {
        this.authorityRepository = authorityRepository;
    }

    public List<Authority> getAll() {
        return authorityRepository.findAll();
    }

    @Transactional
    public Authority create(String name) {
        Authority authority = new Authority();
        authority.setName(name);
        return authorityRepository.save(authority);
    }

    @Transactional
    public void delete(String name) {
        authorityRepository.deleteById(name);
    }
}
