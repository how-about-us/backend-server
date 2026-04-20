package com.howaboutus.backend.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.howaboutus.backend.auth.service.JwtProvider;
import com.howaboutus.backend.common.error.CustomException;
import com.howaboutus.backend.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 access_token 쿠키가 있으면 SecurityContext에 userId를 세팅한다")
    void setsSecurityContextWithValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "valid-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtProvider.extractUserId("valid-jwt")).willReturn(42L);

        filter.doFilterInternal(request, response, filterChain);

        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        assertThat(principal).isEqualTo(42L);
    }

    @Test
    @DisplayName("access_token 쿠키가 없으면 SecurityContext를 비워둔다")
    void doesNotSetSecurityContextWithoutCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("토큰 검증 실패 시 SecurityContext를 비워둔다")
    void doesNotSetSecurityContextOnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "bad-jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(jwtProvider.extractUserId("bad-jwt"))
                .willThrow(new CustomException(ErrorCode.INVALID_TOKEN));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
