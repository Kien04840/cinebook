package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKpiResponse {
    private Long totalUsers;
    private Long newUsersInPeriod;
    private Long activeUsers;
    private Long blockedUsers;
}

