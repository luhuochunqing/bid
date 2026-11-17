package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual Sidecar process and independent PDF rule-column oracle; private customer bytes stay out of Git. */
final class Project1133OfflineFixture implements AutoCloseable {
    static final String PDF_SHA = "de10e5b687aa8eb503c962de005b9d873d9e6d5e22cdeca00283ea5bade84655";
    static final String KEY = "project1133-offline-test-only";
    final Path directory;
    final Path sidecar;
    final Path pdf;
    final String url;
    private final Process process;

    Project1133OfflineFixture(Path directory) throws Exception {
        this.directory = directory;
        sidecar = Path.of("../document-converter-sidecar").toAbsolutePath().normalize();
        pdf = Path.of(System.getProperty("scoreparse.realPdf")).toAbsolutePath();
        assertThat(sha(Files.readAllBytes(pdf))).as("fixed project1133 original PDF").isEqualTo(PDF_SHA);
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        url = "http://127.0.0.1:" + port;
        String python = System.getenv("SIDECAR_PYTHON");
        assertThat(python).as("explicit existing Sidecar Python environment").isNotBlank();
        var builder = new ProcessBuilder(python, "-B", "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", String.valueOf(port))
                .directory(sidecar.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("sidecar.log").toFile());
        builder.environment().put("SIDECAR_SHARED_KEY", KEY);
        process = builder.start();
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline && process.isAlive()) {
                try {
                    var response = client.send(HttpRequest.newBuilder(URI.create(url + "/health"))
                            .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() == 200) return;
                } catch (java.io.IOException unavailable) { /* Actual process is still starting. */ }
                Thread.sleep(100);
            }
            throw new IllegalStateException("Actual Sidecar did not start; see " + directory.resolve("sidecar.log"));
        } catch (Exception failure) { close(); throw failure; }
    }

    JsonNode originalRules() throws Exception {
        String script = """
                import json,sys,pdfplumber
                items=[]
                with pdfplumber.open(sys.argv[1]) as pdf:
                    for page_number in (55,56,57):
                        for table in pdf.pages[page_number-1].extract_tables():
                            for row in table:
                                if len(row)!=4: continue
                                name=''.join((row[1] or '').split())
                                weight=(row[2] or '').strip()
                                rule=row[3] or ''
                                if name=='合计' or name=='评分因素': continue
                                if name and weight.isdigit():
                                    items.append(dict(name=name,weight=weight,rule=rule,page=page_number))
                                elif not name and not weight and rule.strip():
                                    assert items, 'orphan original PDF continuation'
                                    items[-1]['rule']+='\\n'+rule
                assert len(items)==12, len(items)
                print(json.dumps(items,ensure_ascii=False))
                """;
        Path output = directory.resolve("independent-original-rules.json");
        Process oracle = new ProcessBuilder(System.getenv("SIDECAR_PYTHON"), "-B", "-c", script, pdf.toString())
                .directory(sidecar.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(output.toFile()).start();
        try {
            assertThat(oracle.waitFor(30, TimeUnit.SECONDS)).as("PDF oracle deadline").isTrue();
            assertThat(oracle.exitValue()).as("independent original PDF extraction").isZero();
            return new ObjectMapper().readTree(output.toFile());
        } finally { if (oracle.isAlive()) oracle.destroyForcibly(); }
    }

    static String normalized(String text) { return text.replaceAll("[\\s|]+", ""); }
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    @Override public void close() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try { if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
        catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
