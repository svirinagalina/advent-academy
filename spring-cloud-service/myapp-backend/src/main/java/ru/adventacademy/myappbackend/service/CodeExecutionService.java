package ru.adventacademy.myappbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.adventacademy.myappbackend.dto.SubmissionResultDto;
import ru.adventacademy.myappbackend.entity.TestCase;
import ru.adventacademy.myappbackend.repository.TestCaseRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeExecutionService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${judge0.url:http://localhost:2358}")
    private String judge0Url;

    @Value("${judge0.timeout:30}")
    private int timeoutSeconds;

    private final TestCaseRepository testCaseRepository;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public SubmissionResultDto executeCodeWithTests(Long taskId, String sourceCode, Integer languageId) {
        List<TestCase> testCases = testCaseRepository.findByTaskId(taskId);

        if (testCases.isEmpty()) {
            return SubmissionResultDto.builder()
                    .passedTests(0)
                    .totalTests(0)
                    .allPassed(false)
                    .message("Для этой задачи нет тест-кейсов")
                    .executionDetails("[]")
                    .build();
        }

        int passedTests = 0;
        List<String> executionDetails = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            try {
                log.info("Executing test case {} for task {}", i + 1, taskId);
                String actualOutput = executeOnJudge0(sourceCode, languageId, testCase.getInput());

                if (actualOutput == null) {
                    executionDetails.add(String.format("Test %d: ERROR - Judge0 не вернул результат", i + 1));
                    continue;
                }

                String expected = testCase.getExpectedOutput().trim();
                String actual = actualOutput.trim();

                if (expected.equals(actual)) {
                    passedTests++;
                    executionDetails.add(String.format("Test %d: PASSED", i + 1));
                    log.info("Test case {} passed", i + 1);
                } else {
                    executionDetails.add(String.format("Test %d: FAILED\nExpected: %s\nActual: %s",
                        i + 1, expected, actual));
                    log.warn("Test case {} failed. Expected: {}, Actual: {}", i + 1, expected, actual);
                }
            } catch (Exception e) {
                log.error("Error executing test case {}: {}", i + 1, e.getMessage(), e);
                executionDetails.add(String.format("Test %d: ERROR - %s", i + 1, e.getMessage()));
            }
        }

        int totalTests = testCases.size();
        boolean allPassed = passedTests == totalTests;

        String message = allPassed
                ? "✅ Все тесты пройдены!"
                : String.format("❌ Пройдено %d из %d тестов", passedTests, totalTests);

        return SubmissionResultDto.builder()
                .passedTests(passedTests)
                .totalTests(totalTests)
                .allPassed(allPassed)
                .message(message)
                .executionDetails(String.join("\n", executionDetails))
                .build();
    }

    private String executeOnJudge0(String sourceCode, Integer languageId, String stdin) throws IOException {
        String submissionsUrl = judge0Url + "/submissions";

        JSONObject json = new JSONObject();
        json.put("source_code", sourceCode);
        json.put("language_id", languageId);
        json.put("stdin", stdin == null ? "" : stdin);
        json.put("cpu_time_limit", 2);
        json.put("memory_limit", 128000);

        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url(submissionsUrl + "?base64_encoded=false&wait=true")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        log.debug("Sending request to Judge0: {}", submissionsUrl);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Judge0 request failed with code: {}", response.code());
                if (response.body() != null) {
                    log.error("Response body: {}", response.body().string());
                }
                return null;
            }

            if (response.body() == null) {
                log.error("Judge0 response body is null");
                return null;
            }

            String responseBody = response.body().string();
            log.debug("Judge0 response: {}", responseBody);

            JSONObject jsonResponse = new JSONObject(responseBody);

            // Проверяем статус выполнения
            if (jsonResponse.has("status")) {
                JSONObject status = jsonResponse.getJSONObject("status");
                int statusId = status.getInt("id");

                // Status ID 3 = Accepted (успешное выполнение)
                if (statusId == 3) {
                    return jsonResponse.optString("stdout", "").trim();
                } else {
                    // Другие статусы (ошибка компиляции, runtime error и т.д.)
                    String statusDesc = status.optString("description", "Unknown error");
                    String stderr = jsonResponse.optString("stderr", "");
                    String compileOutput = jsonResponse.optString("compile_output", "");

                    log.warn("Judge0 execution failed. Status: {}, stderr: {}, compile_output: {}",
                            statusDesc, stderr, compileOutput);

                    if (!stderr.isEmpty()) {
                        return "Runtime Error: " + stderr;
                    } else if (!compileOutput.isEmpty()) {
                        return "Compilation Error: " + compileOutput;
                    } else {
                        return "Error: " + statusDesc;
                    }
                }
            }

            return jsonResponse.optString("stdout", "").trim();
        } catch (Exception e) {
            log.error("Exception while calling Judge0: {}", e.getMessage(), e);
            throw new IOException("Failed to execute code on Judge0: " + e.getMessage(), e);
        }
    }
}