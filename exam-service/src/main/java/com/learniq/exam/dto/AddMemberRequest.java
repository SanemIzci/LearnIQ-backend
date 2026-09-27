package com.learniq.exam.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class AddMemberRequest {
    private UUID userId;  // teacherId or studentId
}
