package com.aiagent.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 用于鉴权和限流的 API 密钥实体。
 */
@Entity
@Table(name = "api_keys")
@Data
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    @JsonIgnore
    @lombok.ToString.Exclude
    private String key;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int rateLimit; // 每分钟的请求上限

    private boolean active = true;
}
