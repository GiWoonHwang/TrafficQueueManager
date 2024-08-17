package com.dustin.flow.controller;

import com.dustin.flow.dto.AllowUserResponse;
import com.dustin.flow.dto.AllowedUserResponse;
import com.dustin.flow.dto.RankNumberResponse;
import com.dustin.flow.dto.RegisterUserResponse;
import com.dustin.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * UserQueueController는 사용자 대기열과 관련된 REST API를 제공하는 컨트롤러 클래스입니다.
 * 이 클래스는 UserQueueService를 통해 대기열에 사용자를 등록하고, 허용된 사용자를 확인하고,
 * 사용자의 대기열 순위를 조회하는 등의 작업을 수행합니다.
 */
@RestController // RESTful 웹 서비스로 기능하는 컨트롤러를 나타냅니다.
@RequestMapping("/api/v1/queue") // 이 컨트롤러의 모든 메서드는 "/api/v1/queue" 경로에 매핑됩니다.
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
public class UserQueueController {

    // UserQueueService를 통해 대기열 관련 로직을 처리합니다.
    private final UserQueueService userQueueService;

    /**
     * 사용자를 대기열에 등록하는 API 엔드포인트입니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param userId 등록할 사용자의 ID
     * @return 사용자 등록에 대한 응답을 담은 Mono<RegisterUserResponse>
     */
    @PostMapping("")
    public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") Long userId) {
        return userQueueService.registerWaitQueue(queue, userId)
                .map(RegisterUserResponse::new); // 등록된 사용자 순위를 포함한 응답을 반환
    }

    /**
     * 지정된 수의 사용자를 대기열에서 허용하는 API 엔드포인트입니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param count 허용할 사용자 수
     * @return 허용된 사용자 수에 대한 응답을 담은 Mono<AllowUserResponse>
     */
    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                             @RequestParam(name = "count") Long count) {
        return userQueueService.allowUser(queue, count)
                .map(allowed -> new AllowUserResponse(count, allowed)); // 실제로 허용된 사용자 수를 반환
    }

    /**
     * 사용자가 대기열에서 허용되었는지 확인하는 API 엔드포인트입니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param userId 사용자의 ID
     * @param token 사용자가 제공한 인증 토큰
     * @return 사용자가 허용되었는지 여부를 담은 Mono<AllowedUserResponse>
     */
    @GetMapping("/allowed")
    public Mono<AllowedUserResponse> isAllowedUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                   @RequestParam(name = "user_id") Long userId,
                                                   @RequestParam(name = "token") String token) {
        return userQueueService.isAllowedByToken(queue, userId, token)
                .map(AllowedUserResponse::new); // 사용자가 허용된 경우 true, 그렇지 않으면 false를 반환
    }

    /**
     * 사용자의 현재 대기열 순위를 조회하는 API 엔드포인트입니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param userId 사용자의 ID
     * @return 사용자의 대기열 순위를 담은 Mono<RankNumberResponse>
     */
    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                                @RequestParam(name = "user_id") Long userId) {
        return userQueueService.getRank(queue, userId)
                .map(RankNumberResponse::new); // 사용자의 대기열 순위를 반환
    }

    /**
     * 사용자가 제공된 토큰으로 인증할 수 있도록 하는 API 엔드포인트입니다.
     * 이 엔드포인트는 사용자의 대기열 등록에 대한 토큰을 생성하고, 이를 쿠키로 반환합니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param userId 사용자의 ID
     * @param exchange ServerWebExchange를 통해 HTTP 응답을 조작
     * @return 생성된 토큰을 담은 Mono<Object>
     */
    @GetMapping("/touch")
    Mono<?> touch(@RequestParam(name = "queue", defaultValue = "default") String queue,
                  @RequestParam(name = "user_id") Long userId,
                  ServerWebExchange exchange) {
        return Mono.defer(() -> userQueueService.generateToken(queue, userId))
                .map(token -> {
                    exchange.getResponse().addCookie(
                            ResponseCookie
                                    .from("user-queue-%s-token".formatted(queue), token)
                                    .maxAge(Duration.ofSeconds(300)) // 토큰 쿠키의 유효 기간을 300초로 설정
                                    .path("/")
                                    .build()
                    );
                    return token; // 생성된 토큰을 반환
                });
    }
}
