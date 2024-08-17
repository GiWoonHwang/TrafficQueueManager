package com.dustin.website;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;


@SpringBootApplication
@Controller
public class WebsiteApplication {
	// RestTemplate을 사용하여 외부 API와 통신합니다.
	RestTemplate restTemplate = new RestTemplate();

	public static void main(String[] args) {
		SpringApplication.run(WebsiteApplication.class, args);
	}

	/**
	 * 애플리케이션의 루트 경로("/")로 접근할 때 호출되는 메서드입니다.
	 * 사용자 ID와 대기열 이름을 기반으로 사용자가 허용되었는지 확인하고,
	 * 허용되지 않은 경우 대기실 페이지로 리다이렉트합니다.
	 *
	 * @param queue 대기열의 이름 (기본값: "default")
	 * @param userId 사용자의 ID
	 * @param request HttpServletRequest를 통해 쿠키와 같은 요청 정보를 가져옵니다.
	 * @return 사용자가 허용되었으면 "index" 페이지를, 그렇지 않으면 대기실 페이지로 리다이렉트합니다.
	 */
	@GetMapping("/")
	public String index(@RequestParam(name = "queue", defaultValue = "default") String queue,
						@RequestParam(name = "user_id") Long userId,
						HttpServletRequest request) {
		// 요청에서 쿠키를 가져옵니다.
		var cookies = request.getCookies();
		var cookieName = "user-queue-%s-token".formatted(queue); // 대기열 이름을 기반으로 쿠키 이름을 생성합니다.

		String token = "";
		if (cookies != null) {
			// 해당 쿠키 이름에 맞는 쿠키를 찾고, 있으면 토큰 값을 가져옵니다.
			var cookie = Arrays.stream(cookies).filter(i -> i.getName().equalsIgnoreCase(cookieName)).findFirst();
			token = cookie.orElse(new Cookie(cookieName, "")).getValue();
		}

		// 외부 API를 호출하기 위한 URI를 생성합니다.
		var uri = UriComponentsBuilder
				.fromUriString("http://127.0.0.1:9010") // 외부 서비스의 기본 URL
				.path("/api/v1/queue/allowed") // 허용된 사용자인지 확인하는 API 경로
				.queryParam("queue", queue) // 대기열 이름을 쿼리 매개변수로 추가
				.queryParam("user_id", userId) // 사용자 ID를 쿼리 매개변수로 추가
				.queryParam("token", token) // 쿠키에서 가져온 토큰을 쿼리 매개변수로 추가
				.encode()
				.build()
				.toUri();

		// 외부 API를 호출하여 사용자가 허용되었는지 확인합니다.
		ResponseEntity<AllowedUserResponse> response = restTemplate.getForEntity(uri, AllowedUserResponse.class);
		if (response.getBody() == null || !response.getBody().allowed()) {
			// 사용자가 허용되지 않았다면 대기실 페이지로 리다이렉트합니다.
			return "redirect:http://127.0.0.1:9010/waiting-room?user_id=%d&redirect_url=%s".formatted(
					userId, "http://127.0.0.1:9000?user_id=%d".formatted(userId));
		}
		// 사용자가 허용되었으면 메인 페이지("index")로 이동합니다.
		return "index";
	}

	/**
	 * AllowedUserResponse는 외부 API 응답을 나타내는 레코드 클래스입니다.
	 * 이 클래스는 사용자가 허용되었는지 여부를 나타내는 boolean 값을 포함합니다.
	 */
	public record AllowedUserResponse(Boolean allowed) {
	}
}
