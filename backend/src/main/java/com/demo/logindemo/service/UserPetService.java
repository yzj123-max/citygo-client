package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.UserPetCreateReq;
import com.demo.logindemo.dto.UserPetResp;
import com.demo.logindemo.dto.UserPetUpdateReq;
import com.demo.logindemo.entity.UserPet;
import com.demo.logindemo.mapper.UserPetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserPetService {

    private static final Set<String> SPECIES =
            Set.of("DOG", "CAT", "OTHER");

    private static final BigDecimal MAX_WEIGHT =
            new BigDecimal("9999.99");

    private final UserPetMapper userPetMapper;

    public UserPetService(UserPetMapper userPetMapper) {
        this.userPetMapper = userPetMapper;
    }

    /**
     * 查询当前用户的宠物列表。
     */
    @Transactional(readOnly = true)
    public List<UserPetResp> listMyPets(Long userId) {
        checkUserId(userId);

        List<UserPet> pets = userPetMapper.selectList(
                new LambdaQueryWrapper<UserPet>()
                        .eq(UserPet::getUserId, userId)
                        .orderByDesc(UserPet::getUpdateTime)
                        .orderByDesc(UserPet::getId)
        );

        return pets.stream()
                .map(this::toResp)
                .toList();
    }

    /**
     * 查询当前用户的一只宠物。
     */
    @Transactional(readOnly = true)
    public UserPetResp getMyPet(Long userId, Long petId) {
        return toResp(findOwnedPet(userId, petId));
    }

    /**
     * 新增宠物。
     */
    @Transactional
    public UserPetResp create(
            Long userId,
            UserPetCreateReq req
    ) {
        checkUserId(userId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        UserPet pet = validatedValues(
                req.getName(),
                req.getSpecies(),
                req.getBreed(),
                req.getWeightKg(),
                req.getNote()
        );

        Date now = new Date();

        pet.setUserId(userId);
        pet.setVersion(0L);
        pet.setDeleted(0);
        pet.setCreateTime(now);
        pet.setUpdateTime(now);

        int inserted = userPetMapper.insert(pet);

        if (inserted != 1) {
            throw new IllegalStateException("新增宠物失败");
        }

        return toResp(pet);
    }

    /**
     * 完整更新宠物资料。
     * 品种、备注传null或空字符串，表示清空。
     */
    @Transactional
    public UserPetResp update(
            Long userId,
            Long petId,
            UserPetUpdateReq req
    ) {
        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        if (req.getVersion() == null
                || req.getVersion() < 0
                || req.getVersion() == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "请提供有效的数据版本version"
            );
        }

        UserPet current = findOwnedPet(userId, petId);

        if (!req.getVersion().equals(current.getVersion())) {
            throw conflict();
        }

        UserPet changes = validatedValues(
                req.getName(),
                req.getSpecies(),
                req.getBreed(),
                req.getWeightKg(),
                req.getNote()
        );

        changes.setId(petId);
        changes.setVersion(req.getVersion());
        changes.setUpdateTime(new Date());

        LambdaUpdateWrapper<UserPet> wrapper =
                new LambdaUpdateWrapper<UserPet>()
                        .eq(UserPet::getId, petId)
                        .eq(UserPet::getUserId, userId)
                        // 显式设置，确保允许清空这两个字段。
                        .set(UserPet::getBreed, changes.getBreed())
                        .set(UserPet::getNote, changes.getNote());

        /*
         * 乐观锁插件根据changes中的version：
         * 1. 在更新条件中加入旧版本；
         * 2. 更新成功后将版本加一。
         */
        int updated = userPetMapper.update(changes, wrapper);

        if (updated != 1) {
            throw conflict();
        }

        return toResp(findOwnedPet(userId, petId));
    }

    /**
     * 逻辑删除自己的宠物。
     */
    @Transactional
    public void delete(Long userId, Long petId) {
        UserPet current = findOwnedPet(userId, petId);

        int deleted = userPetMapper.delete(
                new LambdaQueryWrapper<UserPet>()
                        .eq(UserPet::getId, petId)
                        .eq(UserPet::getUserId, userId)
                        .eq(UserPet::getVersion, current.getVersion())
        );

        if (deleted != 1) {
            throw conflict();
        }
    }

    /**
     * 只查询属于当前用户且未删除的记录。
     * @TableLogic会自动排除已删除数据。
     */
    private UserPet findOwnedPet(Long userId, Long petId) {
        checkUserId(userId);

        if (petId == null || petId <= 0) {
            throw new IllegalArgumentException("宠物ID无效");
        }

        UserPet pet = userPetMapper.selectOne(
                new LambdaQueryWrapper<UserPet>()
                        .eq(UserPet::getId, petId)
                        .eq(UserPet::getUserId, userId)
        );

        if (pet == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "宠物不存在或无权访问"
            );
        }

        return pet;
    }

    /**
     * 校验并规范化填写内容。
     */
    private UserPet validatedValues(
            String name,
            String species,
            String breed,
            BigDecimal weightKg,
            String note
    ) {
        name = clean(name);
        species = clean(species);
        breed = clean(breed);
        note = clean(note);

        if (name == null) {
            throw new IllegalArgumentException("宠物名字不能为空");
        }

        checkLength(name, 50, "宠物名字");

        if (species == null) {
            throw new IllegalArgumentException("宠物类型不能为空");
        }

        species = species.toUpperCase(Locale.ROOT);

        if (!SPECIES.contains(species)) {
            throw new IllegalArgumentException(
                    "宠物类型只能是DOG、CAT或OTHER"
            );
        }

        checkLength(breed, 100, "宠物品种");
        checkLength(note, 1000, "宠物备注");

        if (weightKg == null) {
            throw new IllegalArgumentException("宠物体重不能为空");
        }

        if (weightKg.signum() <= 0
                || weightKg.compareTo(MAX_WEIGHT) > 0) {
            throw new IllegalArgumentException(
                    "宠物体重必须大于0且不超过9999.99千克"
            );
        }

        try {
            weightKg = weightKg.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "宠物体重最多保留两位小数"
            );
        }

        UserPet pet = new UserPet();
        pet.setName(name);
        pet.setSpecies(species);
        pet.setBreed(breed);
        pet.setWeightKg(weightKg);
        pet.setNote(note);

        return pet;
    }

    private void checkUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String result = value.strip();
        return result.isEmpty() ? null : result;
    }

    private void checkLength(
            String value,
            int maxLength,
            String fieldName
    ) {
        if (value != null
                && value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过" + maxLength + "个字符"
            );
        }
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "宠物资料已发生变化，请重新加载后操作"
        );
    }

    private UserPetResp toResp(UserPet pet) {
        UserPetResp resp = new UserPetResp();

        resp.setPetId(pet.getId());
        resp.setName(pet.getName());
        resp.setSpecies(pet.getSpecies());
        resp.setBreed(pet.getBreed());
        resp.setWeightKg(pet.getWeightKg());
        resp.setNote(pet.getNote());
        resp.setVersion(pet.getVersion());
        resp.setCreateTime(pet.getCreateTime());
        resp.setUpdateTime(pet.getUpdateTime());

        return resp;
    }
}