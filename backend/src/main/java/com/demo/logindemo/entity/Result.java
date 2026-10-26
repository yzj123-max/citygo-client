package com.demo.logindemo.entity;

public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    //成功，带数据
    public static <T> Result<T> success(T data){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("操作成功");
        r.setData(data);
        return r;
    }
    //成功，不带data
    public static <T> Result<T> success(){
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMsg("操作成功");
        return r;
    }
    //失败
    public static <T> Result<T> fail(String msg){
        Result<T> r = new Result<>();
        r.setCode(400);
        r.setMsg(msg);
        return r;
    }
    //401未登录
    public static <T> Result<T> unauthorized(String msg){
        Result<T> r = new Result<>();
        r.setCode(401);
        r.setMsg(msg);
        return r;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
