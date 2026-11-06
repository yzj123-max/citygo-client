package com.demo.logindemo.service;

import com.demo.logindemo.controller.TravelPlanController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelTaskAccessTest {
    @Test
    void bothTaskEndpointsOnlyExposeTasksToTheirOwner() throws Exception {
        ObjectMapper json = new ObjectMapper();
        TravelPlanService service = new TravelPlanService(
                null, mock(TravelAsyncService.class), null, null, null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TravelPlanController(service, json)).build();
        String response = mvc.perform(post("/travel/generateTask")
                        .requestAttr("userId", 1L)
                        .contentType("application/json")
                        .content("{\"city\":\"长沙\",\"dayNum\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        String taskId = json.readTree(response).get("taskId").asText();
        assertNotNull(service.getTask(1L, taskId));

        mvc.perform(get("/travel/task/{taskId}", taskId).requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(get("/travel/task/{taskId}", taskId).requestAttr("userId", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/travel/getTask").param("taskId", taskId).requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(get("/travel/getTask").param("taskId", taskId).requestAttr("userId", 2L))
                .andExpect(status().isNotFound());
    }
}
