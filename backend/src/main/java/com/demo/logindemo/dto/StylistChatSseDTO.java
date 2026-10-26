package com.demo.logindemo.dto;

import lombok.Data;
import java.util.List;

@Data
public class StylistChatSseDTO {
    private TravelScheduleDTO travelSchedule;
    private Long attractionId;
    private List<String> imgBase64List;
    private String userQuery;
}