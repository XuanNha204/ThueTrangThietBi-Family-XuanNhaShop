package vn.xuannhashop;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.jobs-enabled=false")
@AutoConfigureMockMvc
class ProductPhotoTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;

    @Test
    void ownerUploadsReplacesAndRemovesPublicPhoto() throws Exception {
        UUID type = fixture();
        String write = "/api/equipment-types/" + type + "/photo";
        String read = "/api/catalog/" + type + "/photo/";
        String key = UUID.randomUUID().toString();
        String photo = upload(write, key, null, true);
        assertThat(upload(write, key, null, true)).isEqualTo(photo);

        mvc.perform(get("/api/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + type + "')].photo.id").value(photo))
            .andExpect(jsonPath("$[?(@.id=='" + type + "')].photo.illustrative").value(true));
        byte[] body = mvc.perform(get(read + photo))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(content().contentType("image/jpeg"))
            .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(body));
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(600);

        mvc.perform(multipart(write).file(png()).with(user("owner")).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isConflict());
        String replacement = upload(write, UUID.randomUUID().toString(), photo, false);
        assertThat(replacement).isNotEqualTo(photo);
        mvc.perform(get(read + photo)).andExpect(status().isNotFound());
        mvc.perform(delete(write + "/" + photo).with(user("owner")).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isConflict());
        mvc.perform(delete(write + "/" + replacement).with(user("owner")).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID())).andExpect(status().isNoContent());
        mvc.perform(get(read + replacement)).andExpect(status().isNotFound());
        mvc.perform(get("/api/catalog"))
            .andExpect(jsonPath("$[?(@.id=='" + type + "')].photo").isEmpty());
        assertThat(db.queryForObject(
            "select count(*) from audit_entry where target_id=? and action='PRODUCT_PHOTO_SAVED'", Integer.class, type)).isEqualTo(2);
    }

    @Test
    void mutationsRequireOwnerAndCsrfAndRejectInvalidImageAndSource() throws Exception {
        UUID type = fixture();
        String write = "/api/equipment-types/" + type + "/photo";
        String key = UUID.randomUUID().toString();
        mvc.perform(multipart(write).file(png()).with(user("stock")).with(csrf())
            .header("Idempotency-Key", key)).andExpect(status().isForbidden());
        mvc.perform(multipart(write).file(png()).with(user("owner"))
            .header("Idempotency-Key", key)).andExpect(status().isForbidden());
        mvc.perform(multipart(write).file(new MockMultipartFile("file", "wrong.png", "image/png", new byte[]{1, 2, 3}))
            .with(user("owner")).with(csrf()).header("Idempotency-Key", key)).andExpect(status().isBadRequest());
        mvc.perform(multipart(write).file(png()).param("sourceUrl", "javascript:invalid")
            .with(user("owner")).with(csrf()).header("Idempotency-Key", key)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select count(*) from equipment_type_photo where equipment_type_id=?", Integer.class, type)).isZero();
    }

    @Test
    void inactiveProductPhotoIsNotPublic() throws Exception {
        UUID type = fixture();
        String photo = upload("/api/equipment-types/" + type + "/photo", UUID.randomUUID().toString(), null, false);
        db.update("update equipment_type set active=false where id=?", type);
        mvc.perform(get("/api/catalog/" + type + "/photo/" + photo)).andExpect(status().isNotFound());
    }

    private UUID fixture() {
        UUID id = UUID.randomUUID();
        db.update("""
            insert into equipment_type(id,code,name,category,description,specifications,icon,daily_rate,deposit)
            values(?,?,'JUNIT photo','Test','Test','Test','fan',100,200)
            """, id, "PHOTO-" + id.toString().substring(0, 8));
        return id;
    }

    private String upload(String path, String key, String expected, boolean illustrative) throws Exception {
        var request = multipart(path).file(png())
            .param("illustrative", String.valueOf(illustrative))
            .param("credit", "Photo test")
            .param("sourceUrl", "https://example.com/photo")
            .with(user("owner")).with(csrf()).header("Idempotency-Key", key);
        if (expected != null) request.param("expectedPhotoId", expected);
        String response = mvc.perform(request).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private MockMultipartFile png() throws Exception {
        var buffer = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1800, 900, BufferedImage.TYPE_INT_ARGB), "png", buffer);
        return new MockMultipartFile("file", "photo.png", "image/png", buffer.toByteArray());
    }
}
