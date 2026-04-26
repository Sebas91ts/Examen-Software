package com.systembpm.system.modules.notification.infrastructure.repository;

import com.systembpm.system.modules.notification.domain.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    long countByUserEmailAndReadFalse(String userEmail);

    Optional<Notification> findByIdAndUserEmail(String id, String userEmail);
}
