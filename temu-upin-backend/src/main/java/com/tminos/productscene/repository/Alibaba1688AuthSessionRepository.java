package com.tminos.productscene.repository;

import com.tminos.productscene.entity.Alibaba1688AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Alibaba1688AuthSessionRepository extends JpaRepository<Alibaba1688AuthSession, Long>, JpaSpecificationExecutor<Alibaba1688AuthSession> {

    Optional<Alibaba1688AuthSession> findBySessionNameIgnoreCase(String sessionName);
}
