package org.pdfweb.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.pdfweb.testsupport.PdfFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Testes de contrato do endpoint {@code POST /content-edit}, cobrindo os dois
 * ramos: entrega síncrona (resultId) e a ponte opcional com OCR (jobId).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentEditControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String uploadBlank(int pages) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", PdfFixtures.blankPdf(pages));
        MvcResult res = mvc.perform(multipart("/files").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("fileId").asText();
    }

    @Test
    void syncContentEditReturnsResultId() throws Exception {
        String fileId = uploadBlank(1);
        String body = """
                {"fileId":"%s","elements":[
                  {"type":"text","page":1,"x":50,"y":50,"text":"ok","font":"helvetica","size":12,"color":"#000000"}
                ]}
                """.formatted(fileId);

        mvc.perform(post("/content-edit").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultId").isNotEmpty())
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());
    }

    @Test
    void hardRedactionWithOcrBridgeIsWired() throws Exception {
        String fileId = uploadBlank(1);
        String body = """
                {"fileId":"%s","ocr":true,"lang":"eng","elements":[
                  {"type":"redact","page":1,"x":20,"y":20,"width":100,"height":30,"color":"#000000","hard":true}
                ]}
                """.formatted(fileId);

        MvcResult res = mvc.perform(post("/content-edit")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        int statusCode = res.getResponse().getStatus();

        // Determinístico em ambos os ambientes:
        // - com Tesseract: 202 + jobId (ponte OCR disparada);
        // - sem Tesseract (host de teste): 503-like + envelope OCR_UNAVAILABLE.
        if (statusCode == 202) {
            assertThat(json.get("jobId").asText()).isNotBlank();
        } else {
            assertThat(json.path("error").path("code").asText()).isEqualTo("OCR_UNAVAILABLE");
        }
    }

    @Test
    void analyzeLayoutReturnsBlocks() throws Exception {
        // Upload de PDF com texto conhecido em posições conhecidas.
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf",
                PdfFixtures.pdfWithTextAt("SEGREDO-123", 72f, 750f, "PUBLICO-ok", 72f, 400f));
        MvcResult up = mvc.perform(multipart("/files").file(file))
                .andExpect(status().isCreated()).andReturn();
        String fileId = mapper.readTree(up.getResponse().getContentAsString()).get("fileId").asText();

        String body = """
                {"fileId":"%s","page":1}
                """.formatted(fileId);

        mvc.perform(post("/analyze-layout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.blocks.length()").value(2))
                .andExpect(jsonPath("$.scanned").value(false));
    }
}
