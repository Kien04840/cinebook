package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatisticsResponse {
    private LocalDateTime from;
    private LocalDateTime to;
    private Long totalUsers;
    private Long newUsersInPeriod;
    private Long activeUsers;
    private Long blockedUsers;
}

