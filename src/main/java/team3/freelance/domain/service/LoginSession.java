package team3.freelance.domain.service;

import java.io.Serializable;

/** 세션에 담는 로그인 정보. 설계의 '세션' 매개변수에 해당한다. */
public record LoginSession(Long userId, String name) implements Serializable {
    public static final String KEY = "loginSession";
}
