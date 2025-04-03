package com.vinorsoft.ecm.domain;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileECMRepository extends ReactiveCrudRepository<FileECM, UUID> {
}
