package com.wealthwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wealthwise.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
}
