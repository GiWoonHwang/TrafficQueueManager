package com.dustin.flow.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * ApplicationAdvice는 전역 예외 처리(글로벌 예외 처리)를 위한 클래스입니다.
 * 이 클래스는 애플리케이션에서 발생하는 특정 예외를 포착하고, 적절한 HTTP 응답을 반환하는 역할을 합니다.
 * 이 클래스는 @RestControllerAdvice 어노테이션을 사용하여 스프링의 글로벌 예외 처리 기능을 제공합니다.
 */
@RestControllerAdvice
public class ApplicationAdvice {

    /**
     * ApplicationException이 발생했을 때 이를 처리하는 핸들러 메서드입니다.
     * @param ex ApplicationException 인스턴스
     * @return HTTP 상태 코드와 함께 예외 메시지를 포함한 응답을 반환하는 Mono<ResponseEntity<ServerExceptionResponse>>
     */
    @ExceptionHandler(ApplicationException.class)
    Mono<ResponseEntity<ServerExceptionResponse>> applicationExceptionHandler(ApplicationException ex) {
        return Mono.just(ResponseEntity
                .status(ex.getHttpStatus()) // 예외에 지정된 HTTP 상태 코드 설정
                .body(new ServerExceptionResponse(ex.getCode(), ex.getReason()))); // 예외 정보를 바디에 담아 응답
    }

    /**
     * ServerExceptionResponse는 클라이언트에게 전달할 예외 정보를 담는 클래스입니다.
     * 이 클래스는 code(예외 코드)와 reason(예외 이유)을 포함하며, 이 정보는 클라이언트에 JSON 형태로 전달됩니다.
     */
    public record ServerExceptionResponse(String code, String reason) {

    }
}
