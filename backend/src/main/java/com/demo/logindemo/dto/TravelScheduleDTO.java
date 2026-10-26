package com.demo.logindemo.dto;

import lombok.Data;
import java.util.List;

@Data
public class TravelScheduleDTO {
    private List<DailyPlan> dailyPlanList;

    @Data
    public static class DailyPlan {
        private String dayName;
        private String spotName;
        private String spotStyle;
        private String spotTone;
        private String spotDesc;
    }
}