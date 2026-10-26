package com.demo.logindemo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<Map<String,Object>> handleDuplicateKey(SQLIntegrityConstraintViolationException e){
        Map<String,Object> res = new HashMap<>();
        String msg = e.getMessage();
        if(msg.contains("Duplicate entry") && msg.contains("user.phone")){
            res.put("code", 400);
            res.put("msg", "该手机号已注册");
        }else{
            res.put("code",500);
            res.put("msg","服务器内部错误");
        }
        return ResponseEntity.ok(res);
    }
}