package spikes.editor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Isolated Standalone JavaFX WebView Host Prototype.
 * 
 * Evaluates real viability of embedding Microsoft Monaco Editor within JavaFX WebView.
 * Runs both interactively and in automated benchmark evaluation mode.
 */
public class StandaloneWebViewHost extends Application {

    private static final int WIDTH = 1100;
    private static final int HEIGHT = 750;

    private WebView webView;
    private WebEngine webEngine;
    private final JavaBridge bridge = new JavaBridge();
    private static final AtomicBoolean autoMode = new AtomicBoolean(false);
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static class JavaBridge {
        public void postMessage(String json) {
            System.out.println("[JavaBridge:Received] " + json);
            if (json.contains("\"type\":\"onBenchmarkComplete\"") || json.contains("BENCHMARK SUITE RESULTS")) {
                System.out.println("[JavaBridge] Benchmark suite completed successfully.");
                String outProp = System.getProperty("spike.out");
                File outFile = (outProp != null && !outProp.isBlank()) ? new File(outProp) :
                        (new File("spikes/editor-parity").exists() ? new File("spikes/editor-parity/benchmark-results-webview.json") : new File("benchmark-results-webview.json"));
                try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                    pw.println(json);
                    System.out.println("[JavaBridge] Saved results to: " + outFile.getAbsolutePath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (autoMode.get()) {
                    finishAndExit(0, "Benchmark finished cleanly");
                }
            }
        }

        public void log(String level, String msg) {
            System.out.println("[WebView:" + level + "] " + msg);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        String autoProp = System.getProperty("spike.auto", "false");
        autoMode.set("true".equalsIgnoreCase(autoProp) || getParameters().getUnnamed().contains("--benchmark"));

        System.out.println("=== Standalone JavaFX WebView Host Starting ===");
        System.out.println("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("Automated Mode: " + autoMode.get());

        webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.setJavaScriptEnabled(true);

        // Error & Alert listeners
        webEngine.setOnError(event -> {
            System.err.println("[WebEngine:OnError] " + event.getMessage());
            if (event.getException() != null) {
                event.getException().printStackTrace();
            }
        });

        webEngine.setOnAlert(event -> System.out.println("[WebEngine:OnAlert] " + event.getData()));

        // Monitor load status
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            System.out.println("[WebEngine:State] " + oldState + " -> " + newState);
            if (newState == Worker.State.SUCCEEDED) {
                onPageLoaded();
            } else if (newState == Worker.State.FAILED) {
                Throwable ex = webEngine.getLoadWorker().getException();
                System.err.println("[WebEngine:Fatal] Load worker failed: " + (ex != null ? ex.getMessage() : "unknown"));
                if (ex != null) ex.printStackTrace();
                if (autoMode.get()) {
                    finishAndExit(1, "Page load failed: " + ex);
                }
            }
        });

        // URL to load: default to local HTTP server or fallback to file URI
        String targetUrl = System.getProperty("spike.url");
        if (targetUrl == null || targetUrl.isBlank()) {
            targetUrl = "http://127.0.0.1:8089/web/index.html";
        }

        System.out.println("[WebEngine:Loading] Loading target: " + targetUrl);
        webEngine.load(targetUrl);

        BorderPane root = new BorderPane(webView);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        primaryStage.setTitle("IDEARM Editor Parity Spike - JavaFX WebView Host");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Safety timeout for automated mode
        if (autoMode.get()) {
            scheduler.schedule(() -> {
                System.err.println("[WebEngine:Timeout] Automated benchmark reached safety timeout (45s).");
                finishAndExit(2, "Timeout");
            }, 45, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("removal")
    private void onPageLoaded() {
        System.out.println("[WebEngine] Installing netscape.javascript.JSObject bridge...");
        try {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("idearmJavaBridge", bridge);

            // Forward console.log to Java stdout
            webEngine.executeScript(
                "window.console.log = function() { " +
                "  var args = Array.prototype.slice.call(arguments).join(' '); " +
                "  if (window.idearmJavaBridge) window.idearmJavaBridge.log('INFO', args); " +
                "};" +
                "window.console.error = function() { " +
                "  var args = Array.prototype.slice.call(arguments).join(' '); " +
                "  if (window.idearmJavaBridge) window.idearmJavaBridge.log('ERROR', args); " +
                "};" +
                "window.console.warn = function() { " +
                "  var args = Array.prototype.slice.call(arguments).join(' '); " +
                "  if (window.idearmJavaBridge) window.idearmJavaBridge.log('WARN', args); " +
                "};"
            );

            String userAgent = (String) webEngine.executeScript("navigator.userAgent");
            System.out.println("[WebEngine:UserAgent] " + userAgent);

            // Record memory usage
            Runtime rt = Runtime.getRuntime();
            long totalMem = rt.totalMemory();
            long freeMem = rt.freeMemory();
            long usedMemMb = (totalMem - freeMem) / (1024 * 1024);
            System.out.println("[Host:Memory] JVM Heap Used: " + usedMemMb + " MB (Total: " + (totalMem / (1024 * 1024)) + " MB)");

            // If autoMode, wait 2 seconds for Monaco AMD init, then trigger benchmark suite
            if (autoMode.get()) {
                scheduler.schedule(() -> {
                    Platform.runLater(() -> {
                        System.out.println("[Host] Triggering in-page runAutomatedBenchmarkSuite()...");
                        try {
                            webEngine.executeScript("runAutomatedBenchmarkSuite();");
                        } catch (Exception ex) {
                            System.err.println("[Host:Error] Failed to execute benchmark script: " + ex.getMessage());
                            ex.printStackTrace();
                            finishAndExit(1, "Script execution error: " + ex.getMessage());
                        }
                    });
                }, 2, TimeUnit.SECONDS);
            }

        } catch (Exception ex) {
            System.err.println("[Host:Exception] Failed during onPageLoaded: " + ex.getMessage());
            ex.printStackTrace();
            if (autoMode.get()) {
                finishAndExit(1, "Bridge injection exception: " + ex.getMessage());
            }
        }
    }

    private static void finishAndExit(int exitCode, String reason) {
        System.out.println("[Host:Exit] Exiting with code " + exitCode + " (" + reason + ")");
        scheduler.shutdown();
        Platform.exit();
        System.exit(exitCode);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
