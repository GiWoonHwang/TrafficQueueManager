package com.dustin.flow.service;

import com.dustin.flow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * UserQueueService는 대기열 시스템에서 사용자를 관리하는 서비스 클래스입니다.
 * 이 클래스는 사용자가 대기열에 등록하고, 특정 사용자들을 대기열에서 진행할 수 있도록 허용하는 등의 기능을 제공합니다.
 * ReactiveRedisTemplate을 사용하여 비동기적으로 Redis에 데이터를 저장하고 조회합니다.
 * 스케줄러를 통해 주기적으로 대기열에서 사용자를 허용하는 작업도 수행합니다.
 */
@Slf4j // 로깅을 위한 Lombok 어노테이션
@Service // 스프링 서비스 빈으로 등록
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
public class UserQueueService {

    // ReactiveRedisTemplate을 사용해 비동기적으로 Redis와 상호작용합니다.
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // 대기열에서 사용자를 기다리게 하는 키 형식
    private final String USER_QUEUE_WAIT_KEY = "users:queue:%s:wait";

    // 모든 대기열 키를 스캔하기 위한 패턴
    private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait";

    // 진행 중인 사용자를 관리하는 키 형식
    private final String USER_QUEUE_PROCEED_KEY = "users:queue:%s:proceed";

    // 스케줄러 활성화 여부를 결정하는 설정 값
    @Value("${scheduler.enabled}")
    private Boolean scheduling = false;

    /**
     * 사용자를 대기열에 등록하는 메서드입니다.
     * @param queue 등록할 대기열의 이름
     * @param userId 등록할 사용자의 ID
     * @return 사용자 순위를 나타내는 Mono<Long>
     */
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        var unixTimestamp = Instant.now().getEpochSecond(); // 현재 시간을 Unix 타임스탬프로 가져옵니다.
        return reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
                .filter(i -> i) // 성공적으로 추가된 경우에만 진행
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build())) // 이미 등록된 경우 에러 반환
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())) // 사용자 순위 반환
                .map(i -> i >= 0 ? i + 1 : i); // 0부터 시작하는 순위를 1부터 시작하도록 변환
    }

    /**
     * 지정된 수의 사용자를 대기열에서 허용합니다.
     * @param queue 대기열의 이름
     * @param count 허용할 사용자 수
     * @return 허용된 사용자 수를 나타내는 Mono<Long>
     */
    public Mono<Long> allowUser(final String queue, final Long count) {
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond())) // 허용된 사용자를 진행 중으로 이동
                .count(); // 허용된 사용자 수 반환
    }

    /**
     * 사용자가 허용된 사용자 목록에 있는지 확인합니다.
     * @param queue 대기열의 이름
     * @param userId 사용자의 ID
     * @return 사용자가 허용된 상태인지 나타내는 Mono<Boolean>
     */
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L) // 사용자가 존재하지 않으면 -1 반환
                .map(rank -> rank >= 0); // 순위가 0 이상이면 허용된 것으로 판단
    }

    /**
     * 사용자가 제공한 토큰이 유효한지 확인합니다.
     * @param queue 대기열의 이름
     * @param userId 사용자의 ID
     * @param token 사용자가 제공한 토큰
     * @return 토큰이 유효한지 나타내는 Mono<Boolean>
     */
    public Mono<Boolean> isAllowedByToken(final String queue, final Long userId, final String token) {
        return this.generateToken(queue, userId)
                .filter(gen -> gen.equalsIgnoreCase(token)) // 생성된 토큰과 제공된 토큰을 비교
                .map(i -> true) // 일치하면 true 반환
                .defaultIfEmpty(false); // 일치하지 않으면 false 반환
    }

    /**
     * 사용자의 현재 대기열에서의 순위를 반환합니다.
     * @param queue 대기열의 이름
     * @param userId 사용자의 ID
     * @return 사용자의 대기열 순위를 나타내는 Mono<Long>
     */
    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L) // 사용자가 대기열에 없으면 -1 반환
                .map(rank -> rank >= 0 ? rank + 1 : rank); // 0부터 시작하는 순위를 1부터 시작하도록 변환
    }

    /**
     * 사용자의 대기열 등록에 대한 토큰을 생성합니다.
     * @param queue 대기열의 이름
     * @param userId 사용자의 ID
     * @return 생성된 토큰을 나타내는 Mono<String>
     */
    public Mono<String> generateToken(final String queue, final Long userId) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256"); // SHA-256 알고리즘을 사용해 해시 생성
            var input = "user-queue-%s-%d".formatted(queue, userId); // 입력 데이터 생성
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8)); // 해시 계산

            StringBuilder hexString = new StringBuilder();
            for (byte aByte: encodedHash) {
                hexString.append(String.format("%02x", aByte)); // 해시를 16진수 문자열로 변환
            }
            return Mono.just(hexString.toString()); // 변환된 문자열 반환
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // 알고리즘이 존재하지 않을 경우 예외 발생
        }
    }

    /**
     * 스케줄러로 주기적으로 호출되어 대기열에서 사용자를 허용하는 작업을 수행합니다.
     * initialDelay와 fixedDelay는 각각 처음 호출 시 지연 시간과 주기적 지연 시간을 나타냅니다.
     */
    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    public void scheduleAllowUser() {
        if (!scheduling) {
            log.info("passed scheduling...");
            return; // 스케줄링이 비활성화된 경우 작업을 건너뜁니다.
        }
        log.info("called scheduling...");

        var maxAllowUserCount = 100L; // 한번에 허용할 최대 사용자 수
        reactiveRedisTemplate.scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_KEY_FOR_SCAN) // 대기열 패턴으로 키 스캔
                        .count(100) // 스캔할 키의 최대 수
                        .build())
                .map(key -> key.split(":")[2]) // 대기열 이름 추출
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed))) // 각 대기열에서 사용자 허용
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1()))) // 허용된 사용자 수 로깅
                .subscribe(); // 작업을 비동기적으로 실행
    }
}
