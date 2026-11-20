package team3.freelance.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 화면(Presentation) 계층 시험: 시연 데이터로 로그인해 실제 HTTP 요청으로 핵심 흐름을 따라간다. */
@SpringBootTest(properties = {"app.seed-demo-data=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WebFlowTest {

    @Autowired MockMvc mvc;

    MockHttpSession login(String email) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/login").session(s).param("email", email).param("password", "1234"))
                .andExpect(redirectedUrl("/"));
        return s;
    }

    @Test
    void 로그인하지_않으면_로그인_화면으로_보낸다() throws Exception {
        mvc.perform(get("/projects/1/proposals")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login").param("email", "client@demo.kr").param("password", "x"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("맞지 않습니다")));
    }

    @Test
    void 모든_화면이_열린다() throws Exception {
        MockHttpSession both = login("both@demo.kr");
        for (String url : new String[]{"/", "/me", "/projects/new", "/projects/1"}) {
            mvc.perform(get(url).session(both)).andExpect(status().isOk());
        }
        mvc.perform(get("/projects/1/agreement").session(both)).andExpect(status().isForbidden())
                .andExpect(content().string(containsString("NOT_PARTICIPANT")));
    }

    @Test
    void 화면으로_선정_합의_제출_검토_완료까지() throws Exception {
        MockHttpSession client = login("client@demo.kr");
        MockHttpSession design = login("design@demo.kr");
        MockHttpSession dev = login("dev@demo.kr");

        // 프리랜서는 제안 목록을 볼 수 없다 (403)
        mvc.perform(get("/projects/1/proposals").session(design)).andExpect(status().isForbidden());

        // S1 선정 (제안 1번 = 박디자)
        mvc.perform(get("/projects/1/proposals").session(client))
                .andExpect(status().isOk()).andExpect(content().string(containsString("박디자")));
        mvc.perform(post("/projects/1/proposals/1/select").session(client))
                .andExpect(redirectedUrl("/projects/1/agreement"));

        // S2 합의: 프리랜서가 고치고(개정 2), 의뢰자가 개정 1로 확인하면 거부, 개정 2로 양측 확인
        mvc.perform(post("/projects/1/agreement").session(design).param("scope", "로고 3종")
                .param("schedule", java.time.LocalDate.now().plusDays(20).toString())
                .param("deliverableFormat", "png, pdf")).andExpect(flash().attributeExists("ok"));
        mvc.perform(post("/projects/1/agreement/confirm").session(client).param("revision", "1"))
                .andExpect(flash().attribute("errorCode", "STALE_REVISION"));
        mvc.perform(post("/projects/1/agreement/confirm").session(client).param("revision", "2"));
        mvc.perform(post("/projects/1/agreement/confirm").session(design).param("revision", "2"))
                .andExpect(flash().attribute("ok", containsString("확정")));
        mvc.perform(get("/projects/1").session(dev)).andExpect(content().string(containsString("작업 중")));
        mvc.perform(get("/projects/1/agreement").session(client)).andExpect(status().isOk())
                .andExpect(content().string(containsString("로고 3종")));
        mvc.perform(get("/projects/1/deliverables/new").session(design)).andExpect(status().isOk())
                .andExpect(content().string(containsString("1회차 제출")));

        // S3 제출: 선정되지 않은 프리랜서는 제출 화면에 들어갈 수 없다
        mvc.perform(get("/projects/1/deliverables/new").session(dev)).andExpect(status().isForbidden());
        mvc.perform(multipart("/projects/1/deliverables").file(new MockMultipartFile("files", "logo.png", "image/png", new byte[]{1, 2}))
                        .param("memo", "1차").session(design))
                .andExpect(redirectedUrl("/projects/1/review"));

        // S4 검토: 피드백 없는 수정 요청은 거부 → 승인 → 완료
        mvc.perform(post("/projects/1/review").session(client).param("decision", "REQUEST_REVISION"))
                .andExpect(flash().attribute("errorCode", "FEEDBACK_REQUIRED"));
        mvc.perform(post("/projects/1/review").session(client).param("decision", "APPROVE"))
                .andExpect(flash().attribute("ok", containsString("승인")));
        mvc.perform(post("/projects/1/complete").session(client))
                .andExpect(flash().attribute("ok", containsString("완료")));
        mvc.perform(get("/projects/1/review").session(design))
                .andExpect(content().string(containsString("완료"))).andExpect(content().string(containsString("성공")));
        mvc.perform(get("/projects/1/history").session(client))
                .andExpect(content().string(containsString("합의 확정")));
    }
}
