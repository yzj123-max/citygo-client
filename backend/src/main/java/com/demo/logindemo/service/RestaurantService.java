package com.demo.logindemo.service;

import com.demo.logindemo.dto.RestaurantQueryDTO;
import com.demo.logindemo.vo.RestaurantVO;
import java.util.List;

public interface RestaurantService {
    List<RestaurantVO> queryRestaurant(RestaurantQueryDTO dto);
}