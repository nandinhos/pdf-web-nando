package org.pdfweb.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.pdfweb.testsupport.PdfFixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void uploadReturnsFileIdAndPageCount() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", PdfFixtures.blankPdf(5));

        mvc.perform(multipart("/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").isNotEmpty())
                .andExpect(jsonPath("$.pages").value(5))
                .andExpect(jsonPath("$.meta.filename").value("sample.pdf"));
    }

    @Test
    void pagesEndpointReturnsPerPageMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.pdf", "application/pdf", PdfFixtures.blankPdf(3));

        MvcResult uploaded = mvc.perform(multipart("/files").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = mapper.readTree(uploaded.getResponse().getContentAsString());
        String fileId = body.get("fileId").asText();

        mvc.perform(get("/files/{id}/pages", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.length()").value(3))
                .andExpect(jsonPath("$.pages[0].width").isNumber());
    }

    @Test
    void unknownFileIdReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/files/{id}/pages", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void emptyUploadReturnsBadRequestEnvelope() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mvc.perform(multipart("/files").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_UPLOAD"));
    }
}
