package com.example.finalexam.repository;

import com.example.finalexam.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    boolean existsByNameAndManagerFullNameAndTeamGroup(String name, String managerFullName, String teamGroup);
}
