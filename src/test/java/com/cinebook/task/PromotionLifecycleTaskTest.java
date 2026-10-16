package com.cinebook.task;

import com.cinebook.service.PromotionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionLifecycleTaskTest {

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private PromotionLifecycleTask promotionLifecycleTask;

    @Test
    void executePromotionLifecycleCheck_Success() {
        when(promotionService.transitionPromotionStatuses(any(LocalDateTime.class))).thenReturn(5);

        promotionLifecycleTask.executePromotionLifecycleCheck();

        verify(promotionService, times(1)).transitionPromotionStatuses(any(LocalDateTime.class));
    }

    @Test
    void executePromotionLifecycleCheck_HandlesExceptionGracefully() {
        when(promotionService.transitionPromotionStatuses(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Should not bubble up exception
        promotionLifecycleTask.executePromotionLifecycleCheck();

        verify(promotionService, times(1)).transitionPromotionStatuses(any(LocalDateTime.class));
    }
}

