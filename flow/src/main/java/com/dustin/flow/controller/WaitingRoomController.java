package com.dustin.flow.controller;

import com.dustin.flow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WaitingRoomController는 사용자가 대기 중인 페이지를 처리하는 컨트롤러입니다.
 * 사용자가 특정 큐에 대기 중일 때 이 페이지를 통해 대기 상태를 확인하거나,
 * 대기열에서 허용된 경우 지정된 리다이렉트 URL로 이동할 수 있습니다.
 */
@Controller // 이 클래스가 스프링 MVC의 컨트롤러임을 나타냅니다.
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
public class WaitingRoomController {

    // 대기열 관련 로직을 처리하기 위해 UserQueueService를 사용합니다.
    private final UserQueueService userQueueService;

    /**
     * 사용자가 웨이팅 룸 페이지에 접속할 때 호출되는 엔드포인트입니다.
     * @param queue 대기열의 이름 (기본값: "default")
     * @param userId 사용자의 ID
     * @param redirectUrl 허용된 후 리다이렉트할 URL
     * @param exchange ServerWebExchange를 통해 요청/응답을 처리
     * @return Rendering 객체를 담은 Mono, 이는 대기 중인 페이지 또는 리다이렉션을 처리합니다.
     */
    @GetMapping("/waiting-room")
    Mono<Rendering> waitingRoomPage(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                    @RequestParam(name = "user_id") Long userId,
                                    @RequestParam(name = "redirect_url") String redirectUrl,
                                    ServerWebExchange exchange) {
        var key = "user-queue-%s-token".formatted(queue); // 쿠키에 저장된 토큰의 키를 생성합니다.
        var cookieValue = exchange.getRequest().getCookies().getFirst(key); // 요청에서 토큰 쿠키를 가져옵니다.
        var token = (cookieValue == null) ? "" : cookieValue.getValue(); // 쿠키가 없으면 빈 문자열을, 있으면 토큰 값을 가져옵니다.

        // 사용자가 대기열에서 허용되었는지 토큰을 통해 확인합니다.
        return userQueueService.isAllowedByToken(queue, userId, token)
                .filter(allowed -> allowed) // 허용된 경우
                .flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build())) // 리다이렉트 URL로 이동
                .switchIfEmpty(
                        // 허용되지 않은 경우, 대기열에 등록하거나 이미 등록된 경우 순위를 가져옵니다.
                        userQueueService.registerWaitQueue(queue, userId)
                                .onErrorResume(ex -> userQueueService.getRank(queue, userId)) // 이미 등록된 경우 에러를 무시하고 순위를 가져옵니다.
                                .map(rank -> Rendering.view("waiting-room.html") // 대기실 페이지를 렌더링
                                        .modelAttribute("number", rank) // 현재 대기 순위를 모델에 추가
                                        .modelAttribute("userId", userId) // 사용자 ID를 모델에 추가
                                        .modelAttribute("queue", queue) // 대기열 이름을 모델에 추가
                                        .build()
                                )
                );
    }
}
