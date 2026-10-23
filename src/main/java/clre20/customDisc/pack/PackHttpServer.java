package clre20.customDisc.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 內建的 HTTP 伺服器，只提供 Java 版資源包下載（只從記憶體送出，不讀任何檔案路徑）。
 *
 * <p>防止被拖垮：同一個 IP 最多同時 2 個下載；下載連續 15 秒沒有進度就中斷連線釋放執行緒，
 * 避免故意不讀取的連線佔滿所有執行緒、讓其他玩家下載不到資源包。</p>
 */
public final class PackHttpServer {

    private static final int THREADS = 8;
    private static final int MAX_PER_IP = 2;
    private static final int CHUNK = 64 * 1024;
    private static final long STALL_SECONDS = 15;

    private final Supplier<PackBuilder.Built> current;
    private final Map<String, Integer> active = new ConcurrentHashMap<>();
    private final Set<Transfer> transfers = ConcurrentHashMap.newKeySet();
    private HttpServer server;
    private ExecutorService executor;
    private ScheduledExecutorService watchdog;
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
        server = HttpServer.create(new InetSocketAddress(host, port), 32);
        executor = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "CustomDisc-HTTP");
            t.setDaemon(true);
            return t;
        });
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CustomDisc-HTTP-Watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(this::checkStalled, 5, 5, TimeUnit.SECONDS);
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
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
        active.clear();
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
            String method = ex.getRequestMethod();
            boolean get = "GET".equalsIgnoreCase(method);
            if (!(get || "HEAD".equalsIgnoreCase(method)) || pack == null
                    || !(path.equals("/customdisc.zip") || path.startsWith("/customdisc-"))) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"CustomDisc.zip\"");
            if (!get) {
                ex.sendResponseHeaders(200, -1);
                return;
            }

            String ip = ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
            if (active.merge(ip, 1, Integer::sum) > MAX_PER_IP) {
                release(ip);
                ex.sendResponseHeaders(429, -1);
                return;
            }
            // 分段送出並記錄進度；連續 STALL_SECONDS 秒送不出去（對方故意不讀）就中斷連線釋放執行緒
            byte[] data = pack.bytes();
            Transfer t = new Transfer(Thread.currentThread());
            transfers.add(t);
            try {
                ex.sendResponseHeaders(200, data.length);
                try (OutputStream out = ex.getResponseBody()) {
                    for (int off = 0; off < data.length; off += CHUNK) {
                        out.write(data, off, Math.min(CHUNK, data.length - off));
                        t.progress = System.nanoTime();
                    }
                }
            } finally {
                transfers.remove(t);
                Thread.interrupted();
                release(ip);
            }
        }
    }

    /** 每 5 秒檢查一次，卡住的下載直接中斷。 */
    private void checkStalled() {
        long limit = System.nanoTime() - TimeUnit.SECONDS.toNanos(STALL_SECONDS);
        for (Transfer t : transfers) {
            if (t.progress < limit) {
                t.worker.interrupt();
            }
        }
    }

    private static final class Transfer {
        final Thread worker;
        volatile long progress = System.nanoTime();

        Transfer(Thread worker) {
            this.worker = worker;
        }
    }

    private void release(String ip) {
        active.computeIfPresent(ip, (k, v) -> v > 1 ? v - 1 : null);
    }
}
