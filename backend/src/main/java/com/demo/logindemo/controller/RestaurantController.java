package com.demo.logindemo.controller;

import com.demo.logindemo.dto.RestaurantQueryDTO;
import com.demo.logindemo.service.RestaurantService;
import com.demo.logindemo.vo.RestaurantVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/restaurant")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @GetMapping("/list")
    public List<RestaurantVO> list(RestaurantQueryDTO dto) {
        return restaurantService.queryRestaurant(dto);
    }
}