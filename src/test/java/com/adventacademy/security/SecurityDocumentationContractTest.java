package com.adventacademy.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security-contract")
class SecurityDocumentationContractTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path MATRIX = ROOT.resolve("docs/security/authorization-matrix.csv");
    private static final Set<String> ACCESS_LEVELS = Set.of(
            "public",
            "published-only",
            "authenticated",
            "self-or-admin",
            "admin",
            "content-editor-or-admin",
            "content-owner-editor-or-admin",
            "published-or-content-authorized",
            "host",
            "host-or-member",
            "one-time-capability",
            "ops-or-orchestrator",
            "local-only",
            "delegated",
            "delegated-and-edge-policy"
    );
    private static final Pattern CLASS_REQUEST_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\"\\s*\\)"
                    + "[\\s\\S]*?(?:public\\s+)?class\\s+\\w+"
    );
    private static final Pattern METHOD_MAPPING = Pattern.compile(
            "@(Get|Post|Put|Patch|Delete)Mapping(?:\\s*\\((.*?)\\))?",
            Pattern.DOTALL
    );
    private static final Pattern QUOTED_VALUE = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern THREAT_ROW = Pattern.compile("^\\| T-\\d{2} .+\\| SEC-\\d{3}.*\\|$");
    private static final Pattern RISK_ROW = Pattern.compile(
            "^\\| R-\\d{3} .+\\| .+\\| .+\\| SEC-\\d{3}(?:[^|]*)\\| 20\\d{2}-\\d{2}-\\d{2} \\| (?:Open|Accepted|Closed) \\|$"
    );
    private static final Pattern PRODUCTION_CONFIG = Pattern.compile(
            "application-(?:production|prod).*\\.(?:yml|yaml|properties)"
    );
    private static final Pattern PRODUCTION_DOCKERFILE = Pattern.compile("Dockerfile\\.(?:production|prod).*");
    private static final List<Pattern> FORBIDDEN_PRODUCTION_PATTERNS = List.of(
            Pattern.compile(
                    "(?m)^\\s*(?:spring\\.profiles\\.active\\s*[=:]\\s*|active:\\s*)(?:local|docker)\\s*$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "(?m)^\\s*(?:management\\.endpoint\\.health\\.show-details\\s*[=:]\\s*|show-details:\\s*)"
                            + "always\\s*$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(
                    "(?ms)^\\s*allowed-origins:\\s*(?:\\[[^]]*\\*[^]]*]|\\R\\s*-\\s*[\"']?\\*[\"']?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)jdwp="),
            Pattern.compile("(?i)\\$\\{[^}:]*(?:PASSWORD|SECRET|PRIVATE_KEY)[^}:]*:[^}]+}"),
            Pattern.compile("(?m)^\\s*xpack\\.security\\.enabled:\\s*[\"']?false", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?m)^\\s*CAMUNDA_.*CSRF.*:\\s*[\"']?false", Pattern.CASE_INSENSITIVE)
    );

    @Test
    void securityDocumentsAndDecisionsExist() {
        final List<String> requiredFiles = List.of(
                "docs/security/README.md",
                "docs/security/threat-model.md",
                "docs/security/authorization-matrix.md",
                "docs/security/authorization-matrix.csv",
                "docs/security/production-security-defaults.md",
                "docs/security/risk-register.md",
                "docs/adr/0002-jwt-key-ownership-and-resource-verification.md",
                "docs/adr/0003-refresh-session-lifecycle.md",
                "docs/adr/0004-untrusted-content-serving.md",
                "docs/adr/0005-trusted-proxy-and-request-identity.md",
                "docs/adr/0006-security-audit-retention-and-access.md",
                "config/security/production-policy.properties"
        );

        for (String requiredFile : requiredFiles) {
            assertTrue(Files.isRegularFile(ROOT.resolve(requiredFile)), "Missing security contract: " + requiredFile);
        }
    }

    @Test
    void authorizationMatrixIsWellFormedAndReferencesRealSources() throws IOException {
        final List<MatrixEntry> entries = readMatrix();
        final Set<RouteKey> keys = new HashSet<>();

        assertTrue(entries.size() >= 80, "The matrix must cover application, gateway, and operations surfaces");
        for (MatrixEntry entry : entries) {
            assertTrue(keys.add(entry.key()), "Duplicate authorization matrix route: " + entry.key());
            assertTrue(ACCESS_LEVELS.contains(entry.currentAccess()),
                    "Unknown current access level for " + entry.key() + ": " + entry.currentAccess());
            assertTrue(ACCESS_LEVELS.contains(entry.targetAccess()),
                    "Unknown target access level for " + entry.key() + ": " + entry.targetAccess());
            assertTrue(entry.tracking().matches("SEC-\\d{3}"), "Missing tracking issue for " + entry.key());
            assertTrue(Files.isRegularFile(ROOT.resolve(entry.source())),
                    "Matrix source does not exist for " + entry.key() + ": " + entry.source());
        }
    }

    @Test
    void everyAnnotatedControllerRouteIsInAuthorizationMatrix() throws IOException {
        final Set<RouteKey> documented = matrixKeys();
        final List<RouteKey> discovered = discoverControllerRoutes();
        final Set<RouteKey> discoveredKeys = new HashSet<>(discovered);

        assertFalse(discovered.isEmpty(), "No controller routes were discovered");
        for (RouteKey route : discovered) {
            assertTrue(documented.contains(route), "Undocumented controller route: " + route);
        }
        for (MatrixEntry entry : readMatrix()) {
            if (entry.source().endsWith("Controller.java")) {
                assertTrue(discoveredKeys.contains(entry.key()),
                        "Stale or misspelled controller route in matrix: " + entry.key());
            }
        }
    }

    @Test
    void everyGatewayPathPredicateIsInAuthorizationMatrix() throws IOException {
        final Set<RouteKey> documented = matrixKeys();
        final Path gatewayConfig = ROOT.resolve("api-gateway/src/main/resources/application.yml");
        final List<String> lines = Files.readAllLines(gatewayConfig);
        int discovered = 0;

        for (String line : lines) {
            final String trimmed = line.trim();
            if (!trimmed.startsWith("- Path=")) {
                continue;
            }
            final String predicates = trimmed.substring("- Path=".length());
            for (String path : predicates.split(",")) {
                final String normalizedPath = normalizePath(path.trim());
                String method = "ANY";
                if ("/ws/meetings".equals(normalizedPath)) {
                    method = "WS";
                }
                final RouteKey route = new RouteKey("api-gateway", method, normalizedPath);
                assertTrue(documented.contains(route), "Undocumented gateway route: " + route);
                discovered++;
            }
        }
        assertTrue(discovered >= 18, "Expected all configured gateway path predicates to be discovered");
    }

    @Test
    void everyThreatAndRiskHasOwnedTreatment() throws IOException {
        final List<String> threatLines = Files.readAllLines(ROOT.resolve("docs/security/threat-model.md"));
        final long threats = threatLines.stream().filter(line -> line.startsWith("| T-")).count();
        final long treatedThreats = threatLines.stream().filter(line -> THREAT_ROW.matcher(line).matches()).count();
        assertEquals(threats, treatedThreats, "Every threat row must end in a SEC tracking issue");
        assertTrue(threats >= 20, "Threat model unexpectedly lost baseline threats");

        final List<String> riskLines = Files.readAllLines(ROOT.resolve("docs/security/risk-register.md"));
        final long risks = riskLines.stream().filter(line -> line.startsWith("| R-")).count();
        final long ownedRisks = riskLines.stream().filter(line -> RISK_ROW.matcher(line).matches()).count();
        assertEquals(risks, ownedRisks, "Every risk must have owner, SEC treatment, review date, and status");
        assertTrue(risks >= 12, "Risk register unexpectedly lost baseline risks");
    }

    @Test
    void productionPolicyIsFailClosedAndProductionFilesContainNoLocalDefaults() throws IOException {
        final Properties policy = new Properties();
        try (var input = Files.newInputStream(ROOT.resolve("config/security/production-policy.properties"))) {
            policy.load(input);
        }

        final Map<String, String> requiredPolicy = Map.of(
                "profiles.forbidden", "local,docker",
                "management.health.show-details", "never",
                "springdoc.enabled", "false",
                "origins.allow-wildcard", "false",
                "debug.jdwp", "false",
                "secrets.allow-defaults", "false",
                "service-ports.public", "false",
                "forwarded-headers.trusted-only", "true",
                "request-id.max-length", "128",
                "audit.allow-sensitive-values", "false"
        );
        for (Map.Entry<String, String> requirement : requiredPolicy.entrySet()) {
            assertEquals(requirement.getValue(), policy.getProperty(requirement.getKey()),
                    "Unsafe or missing production policy: " + requirement.getKey());
        }

        try (Stream<Path> paths = Files.walk(ROOT)) {
            final List<Path> productionFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isProductionConfiguration)
                    .toList();
            for (Path productionFile : productionFiles) {
                final String content = Files.readString(productionFile);
                assertTrue(findForbiddenProductionPatterns(content).isEmpty(),
                        "Forbidden local/debug production setting in " + ROOT.relativize(productionFile)
                                + ": " + findForbiddenProductionPatterns(content));
            }
        }
    }

    @Test
    void productionGuardDetectsRepresentativeUnsafeYamlPropertiesAndRuntimeSettings() {
        final List<String> unsafeExamples = List.of(
                "spring:\n  profiles:\n    active: docker\n",
                "spring.profiles.active=local\n",
                "management:\n  endpoint:\n    health:\n      show-details: always\n",
                "management.endpoint.health.show-details=always\n",
                "meeting:\n  allowed-origins:\n    - \"*\"\n",
                "JAVA_TOOL_OPTIONS=jdwp=transport=dt_socket\n",
                "JWT_SECRET=${JWT_SECRET:development-secret}\n",
                "xpack.security.enabled: false\n",
                "CAMUNDA_OPERATE_CSRFPREVENTIONENABLED: \"false\"\n"
        );

        for (String unsafeExample : unsafeExamples) {
            assertFalse(findForbiddenProductionPatterns(unsafeExample).isEmpty(),
                    "Production guard did not reject: " + unsafeExample);
        }
    }

    @Test
    void productionGuardAcceptsRepresentativeFailClosedSettings() {
        final String safeExample = """
                spring.profiles.active=production
                management.endpoint.health.show-details=never
                springdoc.api-docs.enabled=false
                springdoc.swagger-ui.enabled=false
                JWT_SECRET=${JWT_SECRET}
                """;

        assertTrue(findForbiddenProductionPatterns(safeExample).isEmpty(),
                "Production guard rejected fail-closed settings");
    }

    private List<MatrixEntry> readMatrix() throws IOException {
        final List<String> lines = Files.readAllLines(MATRIX);
        assertFalse(lines.isEmpty(), "Authorization matrix is empty");
        assertEquals("surface;method;path;current_access;target_access;owner;source;tracking", lines.getFirst());

        final List<MatrixEntry> entries = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            final String line = lines.get(index);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            final String[] columns = line.split(";", -1);
            assertEquals(8, columns.length, "Invalid matrix row " + (index + 1));
            assertTrue(Arrays.stream(columns).noneMatch(String::isBlank), "Blank matrix value at row " + (index + 1));
            entries.add(new MatrixEntry(
                    columns[0],
                    columns[1],
                    normalizePath(columns[2]),
                    columns[3],
                    columns[4],
                    columns[5],
                    columns[6],
                    columns[7]
            ));
        }
        return entries;
    }

    private Set<RouteKey> matrixKeys() throws IOException {
        final Set<RouteKey> keys = new HashSet<>();
        for (MatrixEntry entry : readMatrix()) {
            keys.add(entry.key());
        }
        return keys;
    }

    private List<RouteKey> discoverControllerRoutes() throws IOException {
        final List<RouteKey> routes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(ROOT, 12)) {
            final List<Path> controllers = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .toList();
            for (Path controller : controllers) {
                routes.addAll(discoverControllerRoutes(controller));
            }
        }
        return routes;
    }

    private List<RouteKey> discoverControllerRoutes(Path controller) throws IOException {
        final String source = Files.readString(controller);
        final Matcher classMapping = CLASS_REQUEST_MAPPING.matcher(source);
        String basePath = "";
        if (classMapping.find()) {
            basePath = classMapping.group(1);
        }
        final Matcher methodMappings = METHOD_MAPPING.matcher(source);
        final List<RouteKey> routes = new ArrayList<>();
        final String surface = ROOT.relativize(controller).getName(0).toString();

        while (methodMappings.find()) {
            final String method = methodMappings.group(1).toUpperCase();
            final String arguments = methodMappings.group(2);
            final List<String> paths = quotedPaths(arguments);
            if (paths.isEmpty()) {
                paths.add("");
            }
            for (String path : paths) {
                routes.add(new RouteKey(surface, method, normalizePath(basePath + path)));
            }
        }
        return routes;
    }

    private List<String> quotedPaths(String arguments) {
        final List<String> paths = new ArrayList<>();
        if (arguments == null) {
            return paths;
        }
        final Matcher values = QUOTED_VALUE.matcher(arguments);
        while (values.find()) {
            paths.add(values.group(1));
        }
        return paths;
    }

    private boolean isProductionConfiguration(Path path) {
        final String name = path.getFileName().toString();
        return PRODUCTION_CONFIG.matcher(name).matches() || PRODUCTION_DOCKERFILE.matcher(name).matches();
    }

    private List<Pattern> findForbiddenProductionPatterns(String content) {
        final List<Pattern> matches = new ArrayList<>();
        for (Pattern forbidden : FORBIDDEN_PRODUCTION_PATTERNS) {
            if (forbidden.matcher(content).find()) {
                matches.add(forbidden);
            }
        }
        return matches;
    }

    private static String normalizePath(String path) {
        assertNotNull(path);
        if (path.isBlank()) {
            return "/";
        }
        String normalized = path;
        if (!path.startsWith("/")) {
            normalized = "/" + path;
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.length() > 1 && normalized.endsWith("/") && !path.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record RouteKey(String surface, String method, String path) {
    }

    private record MatrixEntry(
            String surface,
            String method,
            String path,
            String currentAccess,
            String targetAccess,
            String owner,
            String source,
            String tracking) {

        private RouteKey key() {
            return new RouteKey(surface, method, path);
        }
    }
}
