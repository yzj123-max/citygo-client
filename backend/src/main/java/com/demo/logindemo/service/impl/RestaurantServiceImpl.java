package com.demo.logindemo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.RestaurantQueryDTO;
import com.demo.logindemo.entity.Restaurant;
import com.demo.logindemo.enums.PetFriendlyEnum;
import com.demo.logindemo.mapper.RestaurantMapper;
import com.demo.logindemo.service.RestaurantService;
import com.demo.logindemo.vo.RestaurantVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class RestaurantServiceImpl implements RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Override
    public List<RestaurantVO> queryRestaurant(RestaurantQueryDTO dto) {
        LambdaQueryWrapper<Restaurant> wrapper = new LambdaQueryWrapper<>();

        if (dto.getCity() != null && !dto.getCity().trim().isEmpty()) {
            wrapper.eq(Restaurant::getCity, dto.getCity().trim());
        }

        String petCode = dto.getPetCode();
        if (petCode != null && !petCode.trim().isEmpty()) {
            PetFriendlyEnum petEnum = PetFriendlyEnum.getByCode(petCode.trim());
            if (petEnum != null) {
                wrapper.eq(Restaurant::getPetFriendly, petEnum.getDbValue());
            }
        }

        List<Restaurant> restaurantList = restaurantMapper.selectList(wrapper);
        List<RestaurantVO> voList = new ArrayList<>();

        for (Restaurant r : restaurantList) {
            RestaurantVO vo = new RestaurantVO();
            vo.setRestId(r.getId());
            vo.setRestName(r.getRestName());
            vo.setCity(r.getCity());
            vo.setDistrict(r.getDistrict());
            vo.setFoodType(r.getFoodType());
            vo.setAvgPrice(r.getAvgPrice());
            vo.setRecommend(r.getRecommend());

            String dbVal = r.getPetFriendly();
            String showText = dbVal;
            for (PetFriendlyEnum e : PetFriendlyEnum.values()) {
                if (e.getDbValue().equals(dbVal)) {
                    showText = e.getShowText();
                    break;
                }
            }
            vo.setPetFriendlyText(showText);
            voList.add(vo);
        }
        return voList;
    }
}