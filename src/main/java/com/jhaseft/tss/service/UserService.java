package com.jhaseft.tss.service;

import com.jhaseft.tss.model.User;
import com.jhaseft.tss.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public Optional<User> getByLogin(String login) {
        return userRepository.findByLogin(login);
    }

    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
