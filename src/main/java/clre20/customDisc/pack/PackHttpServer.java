package clre20.customDisc.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** 內建的 HTTP 伺服器，只提供 Java 版資源包下載。 */
public final class PackHttpServer {

    private final Supplier<PackBuilder.Built> current;
    private HttpServer server;
    private ExecutorService executor;
    private String boundHost;
    private int boundPort = -1;

    public PackHttpServer(Supplier<PackBuilder.Built> current) {
        this.current = current;
    }

    public synchronized void start(String host, int port) throws IOException {
        if (server != null && host.equals(boundHost) && port == boundPort) {
            return;
        }
        stop();
        server = HttpServer.create(new InetSocketAddress(host, port), 16);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "CustomDisc-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        boundHost = host;
        boundPort = port;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        boundHost = null;
        boundPort = -1;
    }

    public synchronized boolean running() {
        return server != null;
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            PackBuilder.Built pack = current.get();
            boolean method = "GET".equalsIgnoreCase(ex.getRequestMethod()) || "HEAD".equalsIgnoreCase(ex.getRequestMethod());
            if (!method || pack == null || !(path.equals("/customdisc.zip") || path.startsWith("/customdisc-"))) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"CustomDisc.zip\"");
            if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, pack.bytes().length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(pack.bytes());
            }
        }
    }
}
