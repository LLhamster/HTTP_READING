package com.example.httpreading.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        CommonResponse<Void> body = CommonResponse.error(40001, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        CommonResponse<Void> body = CommonResponse.error(40002, msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // @ExceptionHandler(Exception.class)
    // public ResponseEntity<CommonResponse<Void>> handleOther(Exception e) {
    //     // 可在此处记录日志
    //     CommonResponse<Void> body = CommonResponse.error(50000, "服务器内部错误");
    //     return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    // }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleOther(Exception e) {
        e.printStackTrace(); // 打印完整堆栈
        CommonResponse<Void> body = CommonResponse.error(50000, "服务器内部错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
}
}