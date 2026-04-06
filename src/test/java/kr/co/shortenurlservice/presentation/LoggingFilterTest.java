package kr.co.shortenurlservice.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.shortenurlservice.application.SimpleShortenUrlService;
import kr.co.shortenurlservice.domain.ShortenUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ShortenUrlRestController.class)
@Import({LoggingFilter.class})
class LoggingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    private String url = "https://naver.com";
    private String json;

    @BeforeEach
    void setUp() throws Exception {
        json = objectMapper.writeValueAsString(new ShortenUrlCreateRequestDto(url));

        ShortenUrl shortenUrl = new ShortenUrl(url, ShortenUrl.generateShortenUrlKey(), LocalDateTime.now());
        ShortenUrlCreateResponseDto response = new ShortenUrlCreateResponseDto(shortenUrl);
        given(simpleShortenUrlService.generateShortenUrl(any()))
                .willReturn(response);
    }

    @MockitoBean
    private SimpleShortenUrlService simpleShortenUrlService;

    @Test
    @DisplayName("요청 시 requestId가 자동 생성되어 응답 헤더에 포함된다: WebMvcTest")
    void shouldGenerateRequestId_whenNoHeaderProvided() throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/shortenUrl")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().exists("X-Request-Id"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
    }

    @Test
    @DisplayName("X-Request-Id 헤더가 있다면 동일한 값이 응답된다: WebMvcTest")
    void shouldReturnSameRequestId_whenCustomHeaderProvided() throws Exception {
        String customRequestId = "custom-request-id";

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/shortenUrl")
                                .header("X-Request-Id", customRequestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                ).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("X-Request-Id", customRequestId));
    }

    @Test
    @DisplayName("동시 요청 시 서로 다른 requestId가 부여된다: WebMvcTest")
    void shouldAssignDifferentRequestIds_whenMultipleRequestSent() throws Exception {
        MvcResult result1 = mockMvc.perform(MockMvcRequestBuilders.post("/shortenUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://www.a.com\"}"))
                .andReturn();

        MvcResult result2 = mockMvc.perform(MockMvcRequestBuilders.post("/shortenUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://www.b.com\"}"))
                .andReturn();

        String id1 = result1.getResponse().getHeader("X-Request-Id");
        String id2 = result2.getResponse().getHeader("X-Request-Id");

        assertThat(id1).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("잘못된 요청 시 4xx 상태 코드가 응답된다: WebMvcTest")
    void shouldReturn4xx_whenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/shortenUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }
}