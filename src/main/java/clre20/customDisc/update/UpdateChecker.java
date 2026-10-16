package clre20.customDisc.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** 從 GitHub Releases 檢查新版本。 */
public final class UpdateChecker {

    public static final String REPO = "clre20/CustomDisc";
    public static final String PAGE = "https://github.com/" + REPO + "/releases";

    /** 檢查結果。latest 為 null 代表查詢失敗或還沒有發佈版本。 */
    public record Result(String current, String latest, boolean newer, String error) {
    }

    private final String current;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateChecker(String current) {
        this.current = current;
    }

    public CompletableFuture<Result> check() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CustomDisc/" + current)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() == 404) {
                        return new Result(current, null, false, "目前還沒有發佈版本");
                    }
                    if (res.statusCode() != 200) {
                        return new Result(current, null, false, "GitHub 回應 " + res.statusCode());
                    }
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : null;
                    if (tag == null) {
                        return new Result(current, null, false, "讀不到版本號");
                    }
                    String latest = strip(tag);
                    return new Result(current, latest, compare(latest, strip(current)) > 0, null);
                })
                .exceptionally(t -> new Result(current, null, false, "無法連線到 GitHub"));
    }

    private static String strip(String v) {
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        return s;
    }

    /** 比較 1.2.3 格式的版本號。 */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? num(x[i]) : 0;
            int yi = i < y.length ? num(y[i]) : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
