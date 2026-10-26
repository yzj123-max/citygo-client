package com.demo.logindemo.controller;

import com.demo.logindemo.dto.UserPetCreateReq;
import com.demo.logindemo.dto.UserPetResp;
import com.demo.logindemo.dto.UserPetUpdateReq;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserPetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/me/pets")
public class UserPetController {

    private static final Logger log =
            LoggerFactory.getLogger(UserPetController.class);

    private final UserPetService userPetService;

    public UserPetController(UserPetService userPetService) {
        this.userPetService = userPetService;
    }

    /**
     * 查询自己的全部宠物。
     * GET /api/me/pets
     */
    @GetMapping
    public ResponseEntity<Result<List<UserPetResp>>> list(
            @RequestAttribute("userId") Long userId
    ) {
        return execute(
                () -> userPetService.listMyPets(userId)
        );
    }

    /**
     * 查询自己的一只宠物。
     * GET /api/me/pets/{petId}
     */
    @GetMapping("/{petId}")
    public ResponseEntity<Result<UserPetResp>> detail(
            @RequestAttribute("userId") Long userId,
            @PathVariable("petId") Long petId
    ) {
        return execute(
                () -> userPetService.getMyPet(userId, petId)
        );
    }

    /**
     * 新增宠物。
     * POST /api/me/pets
     */
    @PostMapping
    public ResponseEntity<Result<UserPetResp>> create(
            @RequestAttribute("userId") Long userId,
            @RequestBody UserPetCreateReq req
    ) {
        return execute(
                () -> userPetService.create(userId, req)
        );
    }

    /**
     * 完整更新宠物资料。
     * PUT /api/me/pets/{petId}
     */
    @PutMapping("/{petId}")
    public ResponseEntity<Result<UserPetResp>> update(
            @RequestAttribute("userId") Long userId,
            @PathVariable("petId") Long petId,
            @RequestBody UserPetUpdateReq req
    ) {
        return execute(
                () -> userPetService.update(userId, petId, req)
        );
    }

    /**
     * 逻辑删除宠物。
     * DELETE /api/me/pets/{petId}
     */
    @DeleteMapping("/{petId}")
    public ResponseEntity<Result<String>> delete(
            @RequestAttribute("userId") Long userId,
            @PathVariable("petId") Long petId
    ) {
        return execute(() -> {
            userPetService.delete(userId, petId);
            return "删除成功";
        });
    }

    /**
     * 统一处理本Controller的业务异常。
     */
    private <T> ResponseEntity<Result<T>> execute(
            Supplier<T> action
    ) {
        try {
            T data = action.get();
            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            String message = e.getReason() == null
                    ? "请求失败"
                    : e.getReason();

            return failure(
                    e.getStatusCode().value(),
                    message
            );

        } catch (IllegalArgumentException e) {
            return failure(400, e.getMessage());

        } catch (Exception e) {
            log.error("宠物管理操作失败", e);
            return failure(
                    500,
                    "服务暂时异常，请稍后重试"
            );
        }
    }

    private <T> ResponseEntity<Result<T>> failure(
            int status,
            String message
    ) {
        Result<T> result = Result.fail(message);
        result.setCode(status);

        return ResponseEntity
                .status(status)
                .body(result);
    }
}
