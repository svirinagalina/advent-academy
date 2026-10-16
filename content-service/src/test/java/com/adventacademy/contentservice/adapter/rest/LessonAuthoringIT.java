package com.adventacademy.contentservice.adapter.rest;

import com.adventacademy.contentservice.AbstractPostgresIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, migrations, HTTP validation, JWT filters and object ownership. */
@AutoConfigureMockMvc
class LessonAuthoringIT extends AbstractPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void assignmentConfigurationIsAtomicVersionedAndOwned() throws Exception {
        var f=fixture(); String path="/api/lessons/"+f.lesson()+"/assignment/configuration";
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(false)).andExpect(jsonPath("$.version").value(0));
        body(put(path),f.owner(),Map.of("required",true,"title","Build API","description","Implement a tested API","version",0),200);
        assertThat(jdbc.queryForObject("SELECT required_assignment FROM content.lessons WHERE id=?",Boolean.class,f.lesson())).isTrue();
        body(put(path),f.owner(),Map.of("required",false,"version",0),409);
        body(put(path),token(UUID.randomUUID(),"INSTRUCTOR"),Map.of("required",false,"version",1),403);
        body(put(path),f.owner(),Map.of("required",true,"title","","description","","version",1),400);
        assertThat(jdbc.queryForObject("SELECT title FROM content.lesson_assignments WHERE lesson_id=?",String.class,f.lesson())).isEqualTo("Build API");
        body(put(path),f.owner(),Map.of("required",false,"version",1),200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content.lesson_assignments WHERE lesson_id=?",Integer.class,f.lesson())).isZero();
    }

    @Test
    void createsReopensUpdatesAndPublishesWithoutExposingDraftToLearner() throws Exception {
        var f = fixture();
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION, f.owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value(""))
                .andExpect(jsonPath("$.version").value(0));
        String source = "# Первая редакция\n\n**Текст**\n\n<script>alert(1)</script>";
        String preview = body(post(f.path()+"/preview"), f.owner(), Map.of("markdown", source), 200);
        String first = save(f, source, 0, 200);
        assertThat(JsonPath.<String>read(first,"$.html")).isEqualTo(JsonPath.read(preview,"$.html"));
        assertThat(JsonPath.<String>read(first,"$.html")).doesNotContain("<script>");
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION, f.owner()))
                .andExpect(jsonPath("$.markdown").value(source)).andExpect(jsonPath("$.version").value(1));
        assertThat(snapshotCount(f)).isZero();
        publish(f, 1, 200);
        // Make the snapshot reachable through the actual learner API.
        UUID course = UUID.randomUUID(), student = UUID.randomUUID();
        jdbc.update("UPDATE content.components SET status='PUBLISHED' WHERE id=?",f.component());
        jdbc.update("INSERT INTO content.courses(id,title,status,version,created_at,updated_at,owner_id) "
                + "VALUES (?,'Course','PUBLISHED',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",course,f.ownerId());
        jdbc.update("INSERT INTO content.course_components(course_id,component_id,position) VALUES (?,?,0)",course,f.component());
        jdbc.update("INSERT INTO content.learning_enrollments(user_id,course_id) VALUES (?,?)",student,course);
        String learner = "/api/learning/courses/"+course+"/lessons/"+f.lesson();
        String oldHtml = JsonPath.read(preview,"$.html");
        mvc.perform(get(learner).header(HttpHeaders.AUTHORIZATION, token(student,"LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value(oldHtml));
        save(f,"# Вторая редакция",2,200);
        mvc.perform(get(learner).header(HttpHeaders.AUTHORIZATION, token(student,"LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value(oldHtml));
        publish(f,3,200);
        mvc.perform(get(learner).header(HttpHeaders.AUTHORIZATION, token(student,"LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.html").value("<h1>Вторая редакция</h1>\n"));
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.version").value(4)).andExpect(jsonPath("$.markdown").value("# Вторая редакция"));
    }

    @Test
    void conflictsDoNotOverwriteSavedSourceAndPublicationChecksSavedVersion() throws Exception {
        var f=fixture();
        save(f,"# First",0,200);
        save(f,"# Lost editor",0,409);
        publish(f,0,409);
        save(f,"# Latest",1,200);
        publish(f,1,409);
        assertThat(snapshotCount(f)).isZero();
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.markdown").value("# Latest")).andExpect(jsonPath("$.version").value(2));
        publish(f,2,200);
        mvc.perform(post("/api/lessons/{id}/content/publish", f.lesson()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT markdown FROM content.lesson_contents WHERE lesson_id=?",String.class,f.lesson())).isEqualTo("# Latest");
    }

    @Test
    void simultaneousFirstSavesHaveExactlyOneWinner() throws Exception {
        var f=fixture(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=List.of("# One","# Two").stream().map(text -> pool.submit(() -> {
                start.await();
                return mvc.perform(put(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("markdown",text,"version",0)))).andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(),futures.get(1).get())).containsExactlyInAnyOrder(200,409);
        }
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void foreignInstructorCannotReadPreviewSavePublishOrUploadAssets() throws Exception {
        var f=fixture(); String foreign=token(UUID.randomUUID(),"INSTRUCTOR");
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,foreign)).andExpect(status().isForbidden());
        body(put(f.path()),foreign,Map.of("markdown","# чужой","version",0),403);
        body(post(f.path()+"/preview"),foreign,Map.of("markdown","# чужой"),403);
        body(post(f.path()+"/publish"),foreign,Map.of("version",0),403);
        mvc.perform(multipart("/api/lessons/{id}/assets",f.lesson()).file(new MockMultipartFile("file","x.png","image/png",java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")))
                .header(HttpHeaders.AUTHORIZATION,foreign)).andExpect(status().isForbidden());
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,token(UUID.randomUUID(),"LEARNER"))).andExpect(status().isForbidden());
        assertThat(snapshotCount(f)).isZero();
    }

    @Test
    void legacyImportIsEditableAndStaleEmptyEditorCannotOverwriteIt() throws Exception {
        var f=fixture();
        mvc.perform(multipart("/api/lessons/{id}/content",f.lesson()).file(new MockMultipartFile("file","x.md","text/markdown","# Imported".getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(status().isCreated());
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.markdown").value("# Imported")).andExpect(jsonPath("$.version").value(1));
        save(f,"stale empty editor",0,409);
        save(f,"# Edited without file",1,200);
        publish(f,2,200);
        assertThat(jdbc.queryForObject("SELECT markdown FROM content.lesson_contents WHERE lesson_id=?",String.class,f.lesson())).isEqualTo("# Edited without file");
    }

    @Test
    void validatesAssetsKeepsRawMarkdownAndNewDraftImagePrivateUntilPublication() throws Exception {
        var f=fixture(); save(f,"# Old",0,200); publish(f,1,200);
        var image=mvc.perform(multipart("/api/lessons/{id}/assets",f.lesson()).file(new MockMultipartFile("file","x.png","image/png",java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")))
                .header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=JsonPath.read(image,"$.id"), source="![image](asset:"+id+")";
        mvc.perform(get("/api/assets/"+id)).andExpect(status().isForbidden());
        String rendered=body(post(f.path()+"/preview"),f.owner(),Map.of("markdown",source),200);
        String saved=save(f,source,2,200);
        assertThat(JsonPath.<String>read(saved,"$.markdown")).isEqualTo(source);
        assertThat(JsonPath.<String>read(saved,"$.html")).isEqualTo(JsonPath.read(rendered,"$.html")).contains("/api/assets/"+id);
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(jsonPath("$.markdown").value(source));
        var other=fixture();
        body(post(other.path()+"/preview"),other.owner(),Map.of("markdown",source),422);
        save(other,"![](/api/assets/"+id+")",0,422);
        publish(f,3,200);
        mvc.perform(get("/api/assets/"+id)).andExpect(status().isOk()).andExpect(content().bytes(java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")));
    }

    @Test
    void componentOwnerCanReadPrivateImageUploadedByDifferentCurator() throws Exception {
        var f=fixture(); save(f,"# Published",0,200); publish(f,1,200);
        String curator=token(UUID.randomUUID(),"CONTENT_EDITOR");
        byte[] image=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        String response=mvc.perform(multipart("/api/lessons/{id}/assets",f.lesson())
                .file(new MockMultipartFile("file","curator.png","image/png",image))
                .header(HttpHeaders.AUTHORIZATION,curator)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assetId=JsonPath.read(response,"$.id"), markdown="![Curator image](asset:"+assetId+")";
        save(f,markdown,2,200);
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value(markdown));
        mvc.perform(get("/api/assets/"+assetId).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(status().isOk()).andExpect(content().bytes(image));
        mvc.perform(get("/api/assets/"+assetId).header(HttpHeaders.AUTHORIZATION,curator))
                .andExpect(status().isOk());
        mvc.perform(get("/api/assets/"+assetId).header(HttpHeaders.AUTHORIZATION,token(UUID.randomUUID(),"INSTRUCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/"+assetId)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT markdown FROM content.lesson_contents WHERE lesson_id=?",String.class,f.lesson()))
                .isEqualTo("# Published");
        publish(f,3,200);
        mvc.perform(get("/api/assets/"+assetId)).andExpect(status().isOk()).andExpect(content().bytes(image));
    }

    @Test
    void zipImportRemainsAvailableAndOpensAsEditableMarkdown() throws Exception {
        var f=fixture(); var bytes=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("lesson.md"));
            zip.write("# ZIP import".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        mvc.perform(multipart("/api/lessons/{id}/content",f.lesson())
                .file(new MockMultipartFile("file","lesson.zip","application/zip",bytes.toByteArray()))
                .header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(status().isCreated());
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.markdown").value("# ZIP import")).andExpect(jsonPath("$.version").value(1));
        save(f,"# ZIP edited",1,200); publish(f,2,200);
        mvc.perform(multipart("/api/lessons/{id}/content",f.lesson())
                .file(new MockMultipartFile("file","again.md","text/markdown",new byte[]{65}))
                .header(HttpHeaders.AUTHORIZATION,f.owner())).andExpect(status().isConflict());
    }

    @Test
    void emptyDraftIsAllowedButCannotBePublishedAndVersionIsMandatory() throws Exception {
        var f=fixture();
        for (String blank : List.of("", " \n\t")) {
            String preview=body(post(f.path()+"/preview"),f.owner(),Map.of("markdown",blank),200);
            assertThat(JsonPath.<String>read(preview,"$.html")).isEmpty();
            assertThat(JsonPath.<String>read(preview,"$.markdown")).isEqualTo(blank);
        }
        save(f,"",0,200); publish(f,1,400);
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.markdown").value("")).andExpect(jsonPath("$.html").value(""));
        body(put(f.path()),f.owner(),Map.of("markdown","source"),400);
        body(post(f.path()+"/publish"),f.owner(),Map.of(),400);
        assertThat(snapshotCount(f)).isZero();
        // Clearing a previously published lesson edits only its draft, including on reopen.
        save(f,"# Published text",1,200); publish(f,2,200);
        save(f,"",3,200);
        mvc.perform(get(f.path()).header(HttpHeaders.AUTHORIZATION,f.owner()))
                .andExpect(jsonPath("$.markdown").value("")).andExpect(jsonPath("$.html").value(""))
                .andExpect(jsonPath("$.version").value(4)).andExpect(jsonPath("$.published").value(true));
        publish(f,4,400);
        save(f," \n\t",4,200); publish(f,5,400);
        assertThat(jdbc.queryForObject("SELECT html FROM content.lesson_contents WHERE lesson_id=?",String.class,f.lesson()))
                .isEqualTo("<h1>Published text</h1>\n");
        assertThat(jdbc.queryForObject("SELECT markdown FROM content.lesson_contents WHERE lesson_id=?",String.class,f.lesson()))
                .isEqualTo("# Published text");
        // Blank text still goes through the byte-size validator before the empty-HTML shortcut.
        save(f," ".repeat(5*1024*1024+1),5,400);
    }

    private int snapshotCount(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM content.lesson_contents WHERE lesson_id=?",Integer.class,f.lesson()); }
    private String save(Fixture f,String text,long version,int status) throws Exception { return body(put(f.path()),f.owner(),Map.of("markdown",text,"version",version),status); }
    private String publish(Fixture f,long version,int status) throws Exception { return body(post(f.path()+"/publish"),f.owner(),Map.of("version",version),status); }
    private String body(MockHttpServletRequestBuilder builder,String token,Object value,int expected) throws Exception {
        return mvc.perform(builder.header(HttpHeaders.AUTHORIZATION,token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
    private Fixture fixture() throws Exception {
        UUID owner=UUID.randomUUID(); String editor=token(owner,"CONTENT_EDITOR");
        UUID component=UUID.fromString(JsonPath.read(body(post("/api/components"),editor,Map.of("title","Module"),201),"$.id"));
        UUID lesson=UUID.fromString(JsonPath.read(body(post("/api/components/"+component+"/lessons"),editor,Map.of("title","Lesson"),201),"$.lessons[0].id"));
        return new Fixture(lesson,component,owner,token(owner,"INSTRUCTOR"));
    }
    private record Fixture(UUID lesson,UUID component,UUID ownerId,String owner) { String path() { return "/api/lessons/"+lesson+"/content/draft"; } }
    private String token(UUID user,String role) {
        var key=Keys.hmacShaKeyFor("test-secret-key-for-testing-purposes-only-32-bytes".getBytes(StandardCharsets.UTF_8));
        return "Bearer "+Jwts.builder().subject(user.toString()).issuer("advent-academy-user-service").audience().add("advent-academy-services").and()
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(3600))).claim("token_type", "access").claim("roles",List.of(role)).signWith(key).compact();
    }
}
