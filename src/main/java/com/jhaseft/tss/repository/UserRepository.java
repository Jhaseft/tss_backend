package com.jhaseft.tss.repository;

import com.jhaseft.tss.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByLogin(String login);
    Optional<User> findByEmail(String email);

    @Query("SELECT COALESCE(MAX(u.id), 0) FROM User u")
    Long findMaxId();
}
