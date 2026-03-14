package com.wealthwise.repository;

import com.wealthwise.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Returns an Optional (best practice to avoid NullPointerExceptions)
    Optional<User> findByEmail(String email);
    
    // Quickly checks if an email exists without loading the whole user object
    boolean existsByEmail(String email);
}