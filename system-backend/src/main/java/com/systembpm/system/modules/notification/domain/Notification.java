package com.systembpm.system.modules.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String userId;
    private String userEmail;
    private String title;
    private String message;
    private String type;
    private Boolean read;
    private LocalDateTime createdAt;
    private String relatedProcessInstanceId;
    private String relatedTaskId;

    public Notification() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getRelatedProcessInstanceId() {
        return relatedProcessInstanceId;
    }

    public void setRelatedProcessInstanceId(String relatedProcessInstanceId) {
        this.relatedProcessInstanceId = relatedProcessInstanceId;
    }

    public String getRelatedTaskId() {
        return relatedTaskId;
    }

    public void setRelatedTaskId(String relatedTaskId) {
        this.relatedTaskId = relatedTaskId;
    }

    public static final class Builder {
        private final Notification notification;

        private Builder() {
            this.notification = new Notification();
        }

        public Builder id(String id) {
            notification.setId(id);
            return this;
        }

        public Builder userId(String userId) {
            notification.setUserId(userId);
            return this;
        }

        public Builder userEmail(String userEmail) {
            notification.setUserEmail(userEmail);
            return this;
        }

        public Builder title(String title) {
            notification.setTitle(title);
            return this;
        }

        public Builder message(String message) {
            notification.setMessage(message);
            return this;
        }

        public Builder type(String type) {
            notification.setType(type);
            return this;
        }

        public Builder read(Boolean read) {
            notification.setRead(read);
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            notification.setCreatedAt(createdAt);
            return this;
        }

        public Builder relatedProcessInstanceId(String relatedProcessInstanceId) {
            notification.setRelatedProcessInstanceId(relatedProcessInstanceId);
            return this;
        }

        public Builder relatedTaskId(String relatedTaskId) {
            notification.setRelatedTaskId(relatedTaskId);
            return this;
        }

        public Notification build() {
            return notification;
        }
    }
}
