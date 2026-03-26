package kr.co.shortenurlservice.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
@AutoConfigureMockMvc
class LoggingFilterSpringBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        ShortenUrlCreateRequestDto request = new ShortenUrlCreateRequestDto("https://naver.com");
        json = objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("요청 시 requestId가 자동으로 생성되어 응답 헤더에 포함된다.")
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
    @DisplayName("X-Request-Id 헤더를 보내면 동일한 값이 응답된다")
    void shouldReturnSameRequestId_whenHeaderProvided() throws Exception {
        String customRequestId = "custom-request-id";

        mockMvc.perform(
                        MockMvcRequestBuilders.post("/shortenUrl")
                                .header("X-Request-Id", customRequestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("X-Request-Id", customRequestId));
    }

    @Test
    @DisplayName("동시 요청 시 서로 다른 requestId가 부여된다")
    void shouldAssignDifferentRequestId_whenMultipleRequests() throws Exception {
        String otherJson = objectMapper.writeValueAsString(new ShortenUrlCreateRequestDto("https://google.com"));

        MvcResult result1 = mockMvc.perform(
                        MockMvcRequestBuilders.post("/shortenUrl")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)

                )
                .andReturn();

        MvcResult result2 = mockMvc.perform(
                MockMvcRequestBuilders.post("/shortenUrl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherJson)
        ).andReturn();

        String id1 = result1.getResponse().getHeader("X-Request-Id");
        String id2 = result2.getResponse().getHeader("X-Request-Id");

        assertThat(id1).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id1).isNotEqualTo(id2);
    }
}