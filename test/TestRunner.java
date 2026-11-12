import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Executes (1) the test cases TC1.1 - TC6.6 of the final report (testdata/test_case_data.csv) and
 * (2) every normal / alternative flow of the use-case descriptions PDF (UC#220 - UC#273, driven by
 * testdata/use_case_data.csv) against the running MEOW Spring Boot application and prints
 * PASS / FAIL / N/A for every case.
 *
 * Run (the app must already be running, started with --meow.seed-demo=true):
 *     java TestRunner.java                       (no compile step needed, Java 17+)
 *     java TestRunner.java http://127.0.0.1:8000 (optional base URL)
 *
 * Exit code: 0 = no FAIL, 1 = at least one FAIL, 2 = could not run (app down / data missing).
 */
public class TestRunner {

    // config
    static String BASE = "http://127.0.0.1:8000";
    static final String DATA = "testdata";
    static final String DEMO_PASSWORD = "MEOWDemo!2026";
    static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    // 1x1 PNG, only used when TestDataLoader has not saved any photos in testdata/photos
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    static List<Path> photoFiles;
    static int photoTurn = 0;

    /** Next test photo: the room pictures written by TestDataLoader, in turn. */
    static byte[] photo() throws Exception {
        if (photoFiles == null) {
            Path dir = Paths.get(DATA, "photos");
            photoFiles = new ArrayList<>();
            if (Files.isDirectory(dir))
                try (var files = Files.list(dir)) {
                    files.filter(f -> f.toString().endsWith(".jpg")).sorted().forEach(photoFiles::add);
                }
        }
        if (photoFiles.isEmpty()) return PNG;
        return Files.readAllBytes(photoFiles.get((photoTurn++ * 7) % photoFiles.size()));
    }
    static final byte[] PDF = "%PDF-1.4 test proof document".getBytes(StandardCharsets.UTF_8);

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // result model
    static class NA extends RuntimeException {
        NA(String why) { super(why); }
    }

    static class Result {
        String id, scenario, status, detail;
    }

    static final List<Result> RESULTS = new ArrayList<>();

    static void tc(String id, String fallbackScenario, Callable<String> body) {
        Result r = new Result();
        r.id = id;
        r.scenario = TC_SCENARIO.getOrDefault(id, fallbackScenario);
        try {
            String note = body.call();
            r.status = "PASS";
            r.detail = note == null ? "" : note;
        } catch (NA na) {
            r.status = "N/A";
            r.detail = na.getMessage();
        } catch (AssertionError ae) {
            r.status = "FAIL";
            r.detail = ae.getMessage();
        } catch (Throwable t) {
            r.status = "FAIL";
            r.detail = "Unexpected error: " + t;
        }
        r.detail = ascii(r.detail);
        RESULTS.add(r);
        System.out.printf("%-7s %-5s %s%n", r.id, r.status, r.scenario);
        if (!r.detail.isEmpty()) System.out.println("          " + r.detail);
    }

    static void check(boolean cond, String failMessage) {
        if (!cond) throw new AssertionError(failMessage);
    }

    static void na(String why) { throw new NA(why); }

    static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c < 128 && c >= 32 ? c : (c == '\n' ? ' ' : '?'));
        return sb.toString();
    }

    // generated test data
    static final Map<String, Map<String, String>> TC_INPUT = new HashMap<>();
    static final Map<String, String> TC_SCENARIO = new HashMap<>();
    static final Map<String, String[]> USER_BY_EMAIL = new HashMap<>();   // email -> {id, role, name, plainPw}
    static final Map<String, String[]> COMPANY_BY_DESIGNER = new HashMap<>(); // designer_id -> {name, description}

    static List<List<String>> readCsv(String file) throws Exception {
        Path p = Paths.get(DATA, file);
        if (!Files.exists(p)) throw new IllegalStateException("Missing " + p + " - run: java TestDataGenerator");
        String s = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows.subList(1, rows.size()); // drop header
    }

    static void loadTestData() throws Exception {
        for (List<String> r : readCsv("test_case_data.csv")) {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String part : r.get(2).split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) kv.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
            TC_INPUT.put(r.get(0), kv);
            TC_SCENARIO.put(r.get(0), r.get(1));
        }
        for (List<String> r : readCsv("use_case_data.csv")) {
            TC_INPUT.computeIfAbsent(r.get(0), k -> new LinkedHashMap<>()).put(r.get(2), r.get(3));
            TC_SCENARIO.put(r.get(0), r.get(1));
        }
        for (List<String> r : readCsv("users.csv"))
            USER_BY_EMAIL.put(r.get(3), new String[]{r.get(0), r.get(1), r.get(2), r.get(4)});
        for (List<String> r : readCsv("id_company_profiles.csv"))
            COMPANY_BY_DESIGNER.put(r.get(1), new String[]{r.get(2), r.get(7)});
    }

    static String in(String tc, String key) {
        String v = TC_INPUT.getOrDefault(tc, Map.of()).get(key);
        if (v == null) throw new IllegalStateException("test_case_data.csv: " + tc + " has no '" + key + "'");
        return v;
    }

    /** "Kitchen (not in seed data, ...)" -> "Kitchen" */
    static String head(String v) {
        int i = v.indexOf(" (");
        return (i > 0 ? v.substring(0, i) : v).trim();
    }

    static String pwFor(String email) {
        String[] u = USER_BY_EMAIL.get(email);
        if (u == null) throw new IllegalStateException(email + " not found in users.csv");
        return u[3];
    }

    // HTTP helpers
    static class Sess {
        String cookie, csrf, email;
    }

    static class Resp {
        int status;
        String body;
        Object json;

        Object at(String... path) {
            Object cur = json;
            for (String k : path) {
                if (!(cur instanceof Map)) return null;
                cur = ((Map<?, ?>) cur).get(k);
            }
            return cur;
        }

        String text(String... path) {
            Object o = at(path);
            return o == null ? null : String.valueOf(o);
        }

        /** all messages + error text, lower-cased, for loose matching. */
        String messages() {
            StringBuilder sb = new StringBuilder();
            Object m = at("messages");
            if (m instanceof List)
                for (Object x : (List<?>) m)
                    if (x instanceof Map) sb.append(((Map<?, ?>) x).get("text")).append(" | ");
            Object e = at("error", "message");
            if (e != null) sb.append(e);
            return sb.toString();
        }

        String brief() { return "HTTP " + status + " " + messages(); }
    }

    static Resp send(String method, String path, Sess s, String contentType, byte[] body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + "/api/v1" + path))
                .timeout(Duration.ofSeconds(30));
        if (s != null) {
            b.header("Cookie", "meow_session=" + s.cookie);
            if (s.csrf != null) b.header("X-CSRF-Token", s.csrf);
        }
        if (contentType != null) b.header("Content-Type", contentType);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        Resp r = new Resp();
        r.status = res.statusCode();
        r.body = new String(res.body(), StandardCharsets.UTF_8);
        try { r.json = Json.parse(r.body); } catch (Exception ignored) { }
        lastSetCookie = res.headers().firstValue("Set-Cookie").orElse(null);
        return r;
    }

    static String lastSetCookie;

    static Resp get(String path, Sess s) throws Exception { return send("GET", path, s, null, null); }

    static Resp json(String method, String path, Sess s, String jsonBody) throws Exception {
        return send(method, path, s, "application/json", jsonBody.getBytes(StandardCharsets.UTF_8));
    }

    static String q(String s) { return Json.quote(s); }

    static class Multipart {
        final String boundary = "----MeowRunner" + UUID.randomUUID().toString().replace("-", "");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Multipart field(String name, String value) throws Exception {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
            return this;
        }

        Multipart file(String name, String filename, String mime, byte[] data) throws Exception {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\""
                    + filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        byte[] build() throws Exception {
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }
    }

    // bookkeeping for the activity report
    static int expUsers = 0, expPublished = 0, expViews = 0, expShortlists = 0;
    static final Set<String> publishedOnce = new HashSet<>();

    // API actions
    static Sess login(String email, String pw) throws Exception {
        Resp r = json("POST", "/auth/session", null, "{\"email\":" + q(email) + ",\"password\":" + q(pw) + "}");
        if (r.status != 200) throw new IllegalStateException("login failed for " + email + ": " + r.brief());
        Sess s = new Sess();
        s.email = email;
        s.cookie = lastSetCookie.split(";", 2)[0].split("=", 2)[1];
        Resp me = get("/auth/session", s);
        s.csrf = me.text("data", "csrfToken");
        return s;
    }

    static Resp registerCustomer(String email, String pw) throws Exception {
        Resp r = json("POST", "/registrations", null,
                "{\"email\":" + q(email) + ",\"password\":" + q(pw) + ",\"confirmPassword\":" + q(pw) + "}");
        if (r.status == 201) expUsers++;
        return r;
    }

    static Resp registerDesigner(String email, String pw, String company, String uen) throws Exception {
        Multipart m = new Multipart().field("email", email).field("password", pw).field("confirmPassword", pw)
                .field("role", "designer").field("company", company).field("uen", uen)
                .file("proof", "proof.pdf", "application/pdf", PDF);
        Resp r = send("POST", "/registrations", null, "multipart/form-data; boundary=" + m.boundary, m.build());
        if (r.status == 201) expUsers++;
        return r;
    }

    static Sess ensureCustomer(String email) throws Exception {
        try { return login(email, pwFor(email)); } catch (IllegalStateException notThere) { }
        Resp r = registerCustomer(email, pwFor(email));
        if (r.status != 201) throw new IllegalStateException("could not create " + email + ": " + r.brief());
        return login(email, pwFor(email));
    }

    static Sess ensureDesigner(String email, Sess admin) throws Exception {
        try { return login(email, pwFor(email)); } catch (IllegalStateException notThere) { }
        String[] u = USER_BY_EMAIL.get(email);
        String[] co = COMPANY_BY_DESIGNER.get(u[0]);
        Resp r = registerDesigner(email, u[3], co == null ? u[2] + " Studio" : co[0], "UEN-" + u[0]);
        if (r.status != 201) throw new IllegalStateException("could not register " + email + ": " + r.brief());
        String id = r.text("data", "id");
        Resp a = json("PUT", "/users/" + id + "/verification", admin, "{\"decision\":\"approve\"}");
        if (a.status != 200) throw new IllegalStateException("could not approve " + email + ": " + a.brief());
        return login(email, u[3]);
    }

    static String createProject(Sess d, String title, String desc, String details, String status) throws Exception {
        Resp r = json("POST", "/projects", d, "{\"title\":" + q(title) + ",\"description\":" + q(desc)
                + ",\"projectDetails\":" + q(details) + ",\"status\":" + q(status) + "}");
        check(r.status == 201, "create project failed: " + r.brief());
        return r.text("data", "id");
    }

    static Resp uploadPhotos(Sess d, String pid, int count) throws Exception {
        Multipart m = new Multipart();
        for (int i = 0; i < count; i++) m.file("photos", "photo" + i + ".jpg", "image/jpeg", photo());
        m.field("coverIndex", "0");
        return send("POST", "/projects/" + pid + "/photos", d, "multipart/form-data; boundary=" + m.boundary, m.build());
    }

    static Resp publish(Sess d, String pid) throws Exception {
        Resp r = json("PUT", "/projects/" + pid + "/publication", d, "{\"action\":\"Publish\"}");
        if (r.status == 200 && publishedOnce.add(pid)) expPublished++;
        return r;
    }

    static Resp recordView(Sess s, String pid) throws Exception {
        Resp r = send("POST", "/projects/" + pid + "/views", s, null, null);
        Object res = r.at("data", "result");
        if (r.status == 200 && res instanceof Number && ((Number) res).intValue() > 0) expViews++;
        return r;
    }

    static Resp shortlist(Sess s, String pid) throws Exception {
        Resp r = send("POST", "/projects/" + pid + "/shortlists", s, null, null);
        Object res = r.at("data", "result");
        if (r.status == 200 && res instanceof Number && ((Number) res).intValue() > 0) expShortlists++;
        return r;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> findMine(Sess d, String pid) throws Exception {
        Resp r = get("/projects?mine=true", d);
        check(r.status == 200, "portfolio request failed: " + r.brief());
        Object list = r.at("data", "idps");
        if (list instanceof List)
            for (Object o : (List<?>) list)
                if (o instanceof Map && pid.equals(((Map<String, Object>) o).get("id"))) return (Map<String, Object>) o;
        return null;
    }

    /** {views, shortlists} for one of the designer's IDPs. */
    static int[] stats(Sess d, String pid) throws Exception {
        Resp r = get("/me/analytics/projects", d);
        check(r.status == 200, "analytics request failed: " + r.brief());
        for (Object o : (List<?>) r.at("data", "projects")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (pid.equals(m.get("id")))
                return new int[]{((Number) m.get("views")).intValue(), ((Number) m.get("shortlists")).intValue()};
        }
        throw new AssertionError("IDP " + pid + " missing from analytics");
    }

    static int[] reportTotals(Sess mgr, String date) throws Exception {
        Resp r = get("/reports/activity?period=daily&date=" + date, mgr);
        check(r.status == 200, "activity report failed: " + r.brief());
        Map<?, ?> t = (Map<?, ?>) r.at("data", "totals");
        return new int[]{n(t.get("registration")), n(t.get("project")), n(t.get("view")), n(t.get("shortlist"))};
    }

    static int n(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }

    static String ids(Resp r, String key) {
        StringBuilder sb = new StringBuilder();
        Object list = r.at("data", key);
        if (list instanceof List)
            for (Object o : (List<?>) list) if (o instanceof Map) sb.append(((Map<?, ?>) o).get("id")).append(',');
        return sb.toString();
    }

    // helpers for the use-case suite
    static final String RUN = Long.toString(System.currentTimeMillis() % 1000000);
    static final String STRONG = "Str0ng!Pass1";
    static Sess S_ADMIN, S_MGR, S_CUST, S_DES, S_DESEMPTY;
    static String DES_ID, CO_NAME, CO_DESC, PID0, PID1, KW, TODAY, CUST_EMAIL, EMPTY_EMAIL;

    static String ue(String tag) { return "uc." + tag + "." + RUN + "@example.com"; }

    static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    static void msg(Resp r, String expected) {
        check(r.messages().contains(expected), "expected message '" + expected + "', got: '" + r.messages() + "' (HTTP " + r.status + ")");
    }

    static Resp rawLogin(String email, String pw) throws Exception {
        return json("POST", "/auth/session", null, "{\"email\":" + q(email) + ",\"password\":" + q(pw) + "}");
    }

    static String regBody(String email, String pw, String confirm) {
        return "{\"email\":" + q(email) + ",\"password\":" + q(pw) + ",\"confirmPassword\":" + q(confirm) + "}";
    }

    static Resp adminCreate(Sess admin, String email, String pw, String confirm, String role) throws Exception {
        return json("POST", "/users", admin, "{\"email\":" + q(email) + ",\"password\":" + q(pw) + ",\"confirmPassword\":"
                + q(confirm) + ",\"role\":" + q(role) + "}");
    }

    /** account row from the admin account list (null when the account does not exist). */
    static Map<?, ?> account(Sess admin, String email) throws Exception {
        Resp r = get("/users?q=" + enc(email), admin);
        check(r.status == 200, "account search failed: " + r.brief());
        Object list = r.at("data", "accounts");
        if (list instanceof List)
            for (Object o : (List<?>) list)
                if (email.equalsIgnoreCase(String.valueOf(((Map<?, ?>) o).get("email")))) return (Map<?, ?>) o;
        return null;
    }

    static String accountId(Sess admin, String email) throws Exception {
        Map<?, ?> a = account(admin, email);
        check(a != null, "account " + email + " not found in the admin account list");
        return String.valueOf(a.get("id"));
    }

    static String accountStatus(Sess admin, String email) throws Exception {
        Map<?, ?> a = account(admin, email);
        return a == null ? null : String.valueOf(a.get("status"));
    }

    static Sess newCustomer(String tag) throws Exception {
        String em = ue(tag);
        Resp r = registerCustomer(em, STRONG);
        check(r.status == 201, "could not register helper customer " + em + ": " + r.brief());
        return login(em, STRONG);
    }

    /** files: {filename, mime, "png" | "exe"} */
    static Resp photos(Sess d, String pid, String cover, String[][] files) throws Exception {
        Multipart m = new Multipart();
        for (String[] f : files)
            m.file("photos", f[0], f[1], f[2].equals("png") ? photo() : "MZ not an image".getBytes(StandardCharsets.UTF_8));
        m.field("coverIndex", cover);
        return send("POST", "/projects/" + pid + "/photos", d, "multipart/form-data; boundary=" + m.boundary, m.build());
    }

    static String rawGet(String path) throws Exception {
        HttpRequest rq = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(30)).GET().build();
        return new String(HTTP.send(rq, HttpResponse.BodyHandlers.ofByteArray()).body(), StandardCharsets.UTF_8);
    }

    static boolean listHas(Resp r, String listKey, String id) {
        return ids(r, listKey).contains(id + ",");
    }

    static void sectionHeader(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println("------------------------------------------------------------------");
    }

    // main
    public static void main(String[] args) throws Exception {
        if (args.length > 0) BASE = args[0].replaceAll("/+$", "");
        String envUrl = System.getenv("MEOW_URL");
        if (args.length == 0 && envUrl != null && !envUrl.isBlank()) BASE = envUrl.replaceAll("/+$", "");

        try { loadTestData(); } catch (Exception e) {
            System.err.println("Cannot read generated test data: " + e.getMessage());
            System.exit(2);
        }
        if (!waitForApp()) {
            System.err.println("The MEOW application is not reachable at " + BASE + " (waited 90 s).");
            System.err.println("Start it first:  java -jar meow.jar --meow.seed-demo=true");
            System.exit(2);
        }

        Sess admin, mgr, cust, des, desEmpty;
        int[] baseline;
        try {
            admin = login("admin@meow.demo", DEMO_PASSWORD);
            mgr = login("manager@meow.demo", DEMO_PASSWORD);
        } catch (Exception e) {
            System.err.println("Cannot log in as the demo admin/manager: " + e.getMessage());
            System.err.println("Start the app with --meow.seed-demo=true on a FRESH database.");
            System.exit(2);
            return;
        }
        String today = LocalDate.now(SGT).toString();
        baseline = reportTotals(mgr, today);

        String custEmail = in("TC1.2", "email");
        String desEmail = head(in("TC2.1", "designer"));
        String emptyEmail = head(in("TC3.6", "designer"));
        try {
            cust = ensureCustomer(custEmail);
            des = ensureDesigner(desEmail, admin);
            desEmpty = ensureDesigner(emptyEmail, admin);
        } catch (Exception e) {
            System.err.println("Could not import the generated users into the app: " + e.getMessage());
            System.exit(2);
            return;
        }
        final Sess fCust = cust, fDes = des, fDesEmpty = desEmpty, fMgr = mgr;
        final String desId = (String) ((Map<?, ?>) get("/auth/session", des).at("data", "user")).get("id");

        System.out.println();
        System.out.println("Running test cases against " + BASE + " (data from ./" + DATA + ")");
        System.out.println("------------------------------------------------------------------");

        // TC1 - user accounts
        final String newEmail = in("TC1.1", "email"), newPw = in("TC1.1", "password");
        tc("TC1.1", "Register valid", () -> {
            String email = newEmail, note = "";
            Resp r = registerCustomer(email, newPw);
            if (r.status == 409) {   // database was not fresh: the account exists from an earlier run
                email = "rerun." + RUN + "@example.com";
                r = registerCustomer(email, newPw);
                note = " ('" + newEmail + "' exists from an earlier run, so '" + email + "' was used)";
            }
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            login(email, newPw);
            return "account created and can log in (redirect to homepage is a browser action, not testable via API)" + note;
        });
        tc("TC1.2", "Register duplicate email", () -> {
            Resp r = registerCustomer(in("TC1.2", "email"), in("TC1.2", "password"));
            check(r.status == 409 || r.status == 400, "expected rejection (409/400), got " + r.brief());
            check(r.messages().toLowerCase().contains("already"), "message should say email already in use, got: " + r.messages());
            return null;
        });
        tc("TC1.3", "Register weak password", () -> {
            Resp r = registerCustomer(in("TC1.3", "email"), in("TC1.3", "password"));
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            check(r.messages().toLowerCase().contains("password"), "expected a password-rule message, got: " + r.messages());
            return null;
        });
        tc("TC1.4", "Register missing field", () -> {
            Resp r = registerCustomer(in("TC1.4", "email"), in("TC1.4", "password"));
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            return null;
        });
        tc("TC1.5", "Login valid", () -> {
            Sess s = login(in("TC1.5", "email"), in("TC1.5", "password"));
            Resp me = get("/auth/session", s);
            check(in("TC1.5", "email").equalsIgnoreCase(me.text("data", "user", "email")), "session belongs to another user");
            return null;
        });
        tc("TC1.6", "Login wrong password", () -> {
            Resp r = json("POST", "/auth/session", null, "{\"email\":" + q(in("TC1.6", "email")) + ",\"password\":" + q(in("TC1.6", "password")) + "}");
            check(r.status == 401, "expected HTTP 401, got " + r.brief());
            Resp unknown = json("POST", "/auth/session", null, "{\"email\":\"nobody.here@example.com\",\"password\":" + q(in("TC1.6", "password")) + "}");
            check(r.messages().equals(unknown.messages()), "error differs between wrong password and unknown email (leaks which is wrong)");
            return "generic message: " + r.messages();
        });
        tc("TC1.7", "Logout", () -> {
            String email = in("TC1.7", "user");
            Sess s = login(email, pwFor(email));
            check(get("/navigation", s).status == 200, "fresh session should reach /navigation");
            Resp out = send("DELETE", "/auth/session", s, null, null);
            check(out.status == 200, "logout failed: " + out.brief());
            Resp after = get("/navigation", s);
            check(after.status == 401, "old session still works after logout: " + after.brief());
            return null;
        });

        // TC2 - company information
        final String[] co = COMPANY_BY_DESIGNER.get(USER_BY_EMAIL.get(desEmail)[0]);
        final String coName = co[0], coDesc = co[1];
        tc("TC2.1", "Create company profile", () -> {
            Resp r = json("PUT", "/me/company", fDes, "{\"name\":" + q(coName) + ",\"description\":" + q(coDesc) + ",\"uen\":\"UEN-IDS-001\",\"year\":2015}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            Resp mine = get("/me/company", fDes);
            check(coName.equals(mine.text("data", "company", "name")), "saved profile not returned, got " + mine.text("data", "company", "name"));
            return null;
        });
        tc("TC2.2", "Missing mandatory field", () -> {
            Resp r = json("PUT", "/me/company", fDes, "{\"name\":\"\",\"description\":\"x\",\"uen\":\"UEN-IDS-001\",\"year\":2015}");
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            check(coName.equals(get("/me/company", fDes).text("data", "company", "name")), "company name changed despite the error");
            return null;
        });
        tc("TC2.3", "Edit company info", () -> {
            String newText = coDesc + " | " + in("TC2.3", "address");
            Resp r = json("PUT", "/me/company", fDes, "{\"name\":" + q(coName) + ",\"description\":" + q(newText) + ",\"uen\":\"UEN-IDS-001\",\"year\":2015}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            Resp pub = get("/designers/" + desId + "/company", fCust);
            check(newText.equals(pub.text("data", "company", "description")), "public page does not show the edit");
            return "company has no address field, so the address text was saved in the description";
        });
        tc("TC2.4", "Customer views company", () -> {
            Resp r = get("/designers/" + desId + "/company", fCust);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(coName.equals(r.text("data", "company", "name")), "company name missing on public page");
            String low = r.body.toLowerCase();
            for (String bad : new String[]{"proof", "password", "private", "internal"})
                check(!low.contains(bad), "public page exposes private data (found '" + bad + "')");
            return null;
        });

        // TC3 - IDP portfolio
        final String[] pid = new String[4]; // 0 = current, 1 = past with images
        tc("TC3.1", "Add current IDP", () -> {
            pid[0] = createProject(fDes, "TC3.1 Current IDP", "Added by the test runner", "Details", "Current");
            Map<String, Object> m = findMine(fDes, pid[0]);
            check(m != null, "IDP not in portfolio");
            check("Current".equals(m.get("stage")), "expected stage Current, got " + m.get("stage"));
            return null;
        });
        tc("TC3.2", "Add past IDP with images", () -> {
            pid[1] = createProject(fDes, "TC3.2 Past IDP", "Past project", "Details", "Past");
            Resp up = uploadPhotos(fDes, pid[1], 3);
            check(up.status == 200, "photo upload failed: " + up.brief());
            Map<String, Object> m = findMine(fDes, pid[1]);
            check(m != null && "Past".equals(m.get("stage")), "IDP not listed as Past");
            int photos = ((List<?>) m.get("photos")).size();
            check(photos == 3, "expected 3 photos, found " + photos);
            return null;
        });
        tc("TC3.3", "Missing title", () -> {
            Resp r = json("POST", "/projects", fDes, "{\"title\":\"\",\"description\":\"d\",\"projectDetails\":\"x\",\"status\":\"Current\"}");
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            return null;
        });
        tc("TC3.4", "Mark current as completed", () -> {
            Resp r = json("PATCH", "/projects/" + pid[0], fDes, "{\"title\":\"TC3.1 Current IDP\",\"description\":\"Added by the test runner\",\"projectDetails\":\"Details\",\"status\":\"Past\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("Past".equals(findMine(fDes, pid[0]).get("stage")), "IDP did not move to Past");
            return "app calls the completed state 'Past'";
        });
        tc("TC3.5", "Edit IDP details", () -> {
            Resp p = publish(fDes, pid[1]);
            check(p.status == 200, "could not publish IDP for the customer view: " + p.brief());
            Resp r = json("PATCH", "/projects/" + pid[1], fDes, "{\"title\":\"TC3.2 Past IDP\",\"description\":\"Updated text\",\"projectDetails\":\"Details\",\"status\":\"Past\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("Updated text".equals(findMine(fDes, pid[1]).get("description")), "portfolio not updated");
            check("Updated text".equals(get("/projects/" + pid[1], fCust).text("data", "idp", "description")), "customer view not updated");
            return "budget is not an editable field in this app; description/title/status were tested";
        });
        tc("TC3.6", "Empty portfolio", () -> {
            Resp r = get("/projects?mine=true", fDesEmpty);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(((List<?>) r.at("data", "idps")).isEmpty(), "portfolio should be empty");
            // The empty-state text is rendered by the web UI (the API just returns an empty list).
            String ui = rawGet("/js/app.js");
            check(ui.contains("You have not created any IDPs yet"), "web UI has no 'no projects yet' empty-state message");
            return "API returns an empty list; the web UI shows 'You have not created any IDPs yet' (checked in the served /js/app.js, not in a browser)";
        });
        // TC4 - customer search
        final String keyword = head(in("TC4.1", "keyword"));
        final String[] kw = new String[1];
        tc("TC4.1", "Keyword search with matches", () -> {
            kw[0] = createProject(fDes, "TC4.1 Keyword IDP", "A " + keyword + " retreat with warm timber", "Details", "Current");
            check(uploadPhotos(fDes, kw[0], 2).status == 200, "photo upload failed");
            check(publish(fDes, kw[0]).status == 200, "publish failed");
            Resp r = get("/projects?q=" + keyword, fCust);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            List<?> list = (List<?>) r.at("data", "idps");
            check(!list.isEmpty(), "no results for '" + keyword + "'");
            check(ids(r, "idps").contains(kw[0]), "the matching IDP is not in the results");
            for (Object o : list) {
                Map<?, ?> m = (Map<?, ?>) o;
                String hay = (m.get("title") + " " + m.get("description") + " " + m.get("projectDetails")).toLowerCase();
                check(hay.contains(keyword.toLowerCase()), "result '" + m.get("title") + "' does not contain the keyword");
            }
            return list.size() + " result(s), all match";
        });
        tc("TC4.2", "Search no results", () -> {
            Resp r = get("/search-results?q=" + enc(head(in("TC4.2", "keyword"))), fCust);   // the search-results page (use case #238)
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(((List<?>) r.at("data", "idps")).isEmpty(), "expected no results");
            check(r.messages().toLowerCase().contains("no projects"), "expected a 'no results' message, got: " + r.messages());
            check(((List<?>) get("/projects?q=" + enc(head(in("TC4.2", "keyword"))), fCust).at("data", "idps")).isEmpty(), "plain project query should also be empty");
            return null;
        });
        tc("TC4.3", "Clear filters", () -> {
            int filtered = ((List<?>) get("/projects?q=" + keyword, fCust).at("data", "idps")).size();
            Resp all = get("/projects?q=", fCust);
            int full = ((List<?>) all.at("data", "idps")).size();
            check(full > 0 && full >= filtered, "full list (" + full + ") is not at least the filtered list (" + filtered + ")");
            return "filtered=" + filtered + ", full=" + full;
        });
        tc("TC4.4", "View IDP details", () -> {
            Resp r = get("/projects/" + kw[0], fCust);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("TC4.1 Keyword IDP".equals(r.text("data", "idp", "title")), "title missing");
            check(r.at("data", "gallery") instanceof List && !((List<?>) r.at("data", "gallery")).isEmpty(), "gallery/images missing");
            check(coName.equals(r.text("data", "idp", "company", "name")), "designer/company info missing");
            return null;
        });
        tc("TC4.5", "Save IDP", () -> {
            Resp r = send("PUT", "/me/favourites/" + kw[0], fCust, null, null);
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            check(ids(get("/me/favourites", fCust), "idps").contains(kw[0]), "IDP not in saved list");
            return null;
        });
        tc("TC4.6", "Persistence", () -> {
            Sess again = login(custEmail, pwFor(custEmail));
            check(ids(get("/me/favourites", again), "idps").contains(kw[0]), "saved IDP missing after logging in again");
            return null;
        });

        // TC5 - tracking
        tc("TC5.1", "View increments", () -> {
            int before = stats(fDes, kw[0])[0];
            Resp r = recordView(fCust, kw[0]);
            check(r.status == 200, "view request failed: " + r.brief());
            int after = stats(fDes, kw[0])[0];
            check(after == before + 1, "view count " + before + " -> " + after + " (expected +1)");
            return null;
        });
        tc("TC5.2", "Shortlist increments", () -> {
            int before = stats(fDes, kw[0])[1];
            Resp r = shortlist(fCust, kw[0]);
            check(r.status == 200, "shortlist request failed: " + r.brief());
            int after = stats(fDes, kw[0])[1];
            check(after == before + 1, "shortlist count " + before + " -> " + after + " (expected +1)");
            return null;
        });
        tc("TC5.3", "No activity", () -> {
            String id = createProject(fDes, "TC5.3 No activity", "d", "d", "Current");
            int[] s = stats(fDes, id);
            check(s[0] == 0 && s[1] == 0, "expected 0 views / 0 shortlists, got " + s[0] + " / " + s[1]);
            return null;
        });
        // TC6 - platform management
        final String[] cat = new String[2]; // id, name
        tc("TC6.1", "Create category", () -> {
            String name = head(in("TC6.1", "name"));
            String existing = get("/categories?flow=edit", fMgr).body;
            String note = "";
            if (existing.toLowerCase().contains("\"name\":\"" + name.toLowerCase() + "\"")) {
                name = name + " QA " + (System.currentTimeMillis() % 100000);
                note = "'" + head(in("TC6.1", "name")) + "' already exists in the app's demo seed, so '" + name + "' was used";
            }
            Resp r = json("POST", "/categories", fMgr, "{\"name\":" + q(name) + ",\"description\":\"\"}");
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            cat[0] = r.text("data", "id");
            cat[1] = name;
            check(get("/categories?flow=edit", fMgr).body.contains(name), "new category not in list");
            check(get("/categories/available", fDes).body.contains(name), "new category not offered in IDP forms");
            return note.isEmpty() ? null : note;
        });
        tc("TC6.2", "Duplicate category", () -> {
            Resp r = json("POST", "/categories", fMgr, "{\"name\":" + q(cat[1]) + ",\"description\":\"\"}");
            check(r.status == 409 || r.status == 400, "expected rejection (409/400), got " + r.brief());
            check(r.messages().toLowerCase().contains("exist"), "expected 'already exists', got: " + r.messages());
            return null;
        });
        tc("TC6.3", "Empty category name", () -> {
            Resp r = json("POST", "/categories", fMgr, "{\"name\":\"\",\"description\":\"\"}");
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            return null;
        });
        tc("TC6.4", "Edit category", () -> {
            String renamed = cat[1] + " Renovated";
            Resp r = json("PATCH", "/categories/" + cat[0], fMgr, "{\"name\":" + q(renamed) + ",\"description\":\"\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(get("/categories?flow=edit", fMgr).body.contains(renamed), "new name not in category list");
            check(get("/categories/available", fDes).body.contains(renamed), "new name not shown in IDP forms");
            cat[1] = renamed;
            return "renamed the category created in TC6.1 (generated id CAT-002 does not exist in the app)";
        });
        tc("TC6.5", "Activity report with data", () -> {
            int[] now = reportTotals(fMgr, today);
            int dUsers = now[0] - baseline[0], dIdps = now[1] - baseline[1], dViews = now[2] - baseline[2], dShort = now[3] - baseline[3];
            String got = "users=" + dUsers + " idps=" + dIdps + " views=" + dViews + " shortlists=" + dShort;
            String exp = "users=" + expUsers + " idps=" + expPublished + " views=" + expViews + " shortlists=" + expShortlists;
            check(got.equals(exp), "report change during this run: " + got + ", expected (counted by the runner): " + exp);
            Resp t = get("/reports/table?period=daily&date=" + today, fMgr);
            check(t.status == 200, "table report failed: " + t.brief());
            List<?> rows = (List<?>) t.at("data", "rows");
            Map<?, ?> total = (Map<?, ?>) rows.get(rows.size() - 1);
            check("Total".equals(total.get("date")), "table has no Total row");
            check(n(total.get("newUsers")) == now[0] && n(total.get("views")) == now[2], "Total row does not match the totals report");
            return exp + " (the API takes period+date, not a range, and cannot back-date events, so today's change is checked)";
        });
        tc("TC6.6", "Report with no data", () -> {
            String[] range = in("TC6.6", "range").split("\\.\\.");
            Resp r = get("/reports/activity?period=daily&date=" + range[0], fMgr);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            int[] z = reportTotals(fMgr, range[0]);
            check(z[0] + z[1] + z[2] + z[3] == 0, "expected zeros, got " + java.util.Arrays.toString(z));
            Resp t = get("/reports/table?period=monthly&date=" + range[0], fMgr);
            check(t.status == 200, "table report errored: " + t.brief());
            return "'" + r.messages() + "'";
        });

        S_ADMIN = admin; S_MGR = mgr; S_CUST = cust; S_DES = des; S_DESEMPTY = desEmpty;
        DES_ID = desId; CO_NAME = coName; CO_DESC = coDesc; PID0 = pid[0]; PID1 = pid[1]; KW = kw[0];
        TODAY = today; CUST_EMAIL = custEmail; EMPTY_EMAIL = emptyEmail;
        runUseCases();

        summary();
    }

    // use-case descriptions (PDF)
    @SuppressWarnings("unchecked")
    static void runUseCases() throws Exception {
        final Sess admin = S_ADMIN, mgr = S_MGR, des = S_DES, desEmpty = S_DESEMPTY;
        final String adminId = String.valueOf(((Map<?, ?>) get("/auth/session", admin).at("data", "user")).get("id"));
        final String[] ucCat = new String[2];   // id, name of the category created in UC#246

        sectionHeader("Use-case descriptions (use-case PDF): normal + alternative flows");

        // accounts
        tc("UC#220", "Fill Customer Registration Form", () -> {
            final String id = "UC#220";
            Resp form = get("/registration-form", null);
            check(form.status == 200 && "customerRegistration".equals(form.text("data", "form")), "step 2: registration form not shown: " + form.brief());
            String em = ue("fill"), pw = in(id, "valid_password");
            Resp ok = json("POST", "/registration-validations", null, regBody(em, pw, pw));
            check(ok.status == 200 && Boolean.TRUE.equals(ok.at("data", "submitEnabled")), "normal flow: Create Account not enabled: " + ok.body);
            Resp bad = json("POST", "/registration-validations", null, regBody(in(id, "bad_email"), pw, pw));   // 6a
            check(bad.status == 400, "6a: expected HTTP 400, got " + bad.brief());
            msg(bad, in(id, "msg_invalid_email"));
            check(!Boolean.TRUE.equals(bad.at("data", "submitEnabled")), "6a: Create Account enabled despite the invalid email");
            Resp mm = json("POST", "/registration-validations", null, regBody(em, pw, in(id, "mismatch_password")));   // 6b
            check(mm.status == 400, "6b: expected HTTP 400, got " + mm.brief());
            msg(mm, in(id, "msg_mismatch"));
            check(account(admin, em) == null, "validating the form must not create an account");
            return null;
        });
        tc("UC#221", "Create Customer Account", () -> {
            final String id = "UC#221";
            String em = ue("cust"), pw = in(id, "password");
            Resp r = json("POST", "/registrations", null, regBody(em, pw, pw));
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Map<?, ?> a = account(admin, em);
            check(a != null && "customer".equals(a.get("role")) && "active".equals(a.get("status")), "saved account is not an active customer: " + a);
            Resp lg = rawLogin(em, pw);
            check(lg.status == 200, "password was saved/hashed wrongly - cannot log in: " + lg.brief());
            Resp dup = json("POST", "/registrations", null, regBody(em, pw, pw));   // 3a
            check(dup.status == 409 || dup.status == 400, "3a: expected rejection, got " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            return null;
        });
        tc("UC#222", "Register Interior Designer Account", () -> {
            final String id = "UC#222";
            Resp form = get("/registration-form?role=designer", null);
            check(form.status == 200 && "designerRegistration".equals(form.text("data", "form")), "step 2: designer form not shown: " + form.brief());
            String em = ue("des"), pw = in(id, "password");
            Resp r = registerDesigner(em, pw, "UC Studio " + RUN, "UEN-UC222-" + RUN);
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Resp v = get("/users/" + r.text("data", "id") + "/verification", admin);
            check(v.status == 200, "admin cannot open the saved application: " + v.brief());
            check(("UC Studio " + RUN).equals(v.text("data", "company", "name")), "company name not saved: " + v.body);
            check(v.text("data", "company", "proofUrl") != null || v.body.toLowerCase().contains("proof"), "business proof not saved with the application: " + v.body);
            Resp dup = registerDesigner(em, pw, "UC Studio 2 " + RUN, "UEN-UC222B-" + RUN);   // 6a
            check(dup.status == 409 || dup.status == 400, "6a: expected rejection, got " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            return null;
        });
        tc("UC#223", "Set Account Pending Verification", () -> {
            final String id = "UC#223";
            String em = ue("pend");
            Resp r = registerDesigner(em, STRONG, "UC Pending " + RUN, "UEN-UC223-" + RUN);
            check(r.status == 201, "registration failed: " + r.brief());
            msg(r, in(id, "msg_success"));
            check("pending".equals(accountStatus(admin, em)), "account status should be pending, is " + accountStatus(admin, em));
            Resp lg = rawLogin(em, STRONG);
            check(lg.status == 200 && "pending".equals(lg.text("data", "user", "status")), "session shows status " + lg.text("data", "user", "status"));
            Sess ps = login(em, STRONG);
            Resp c = json("POST", "/projects", ps, "{\"title\":\"x\",\"description\":\"d\",\"projectDetails\":\"x\",\"status\":\"Current\"}");
            check(c.status == 403, "step 3: publishing must stay off until verified, but an IDP could be created: " + c.brief());
            return "pending designer is blocked from creating/publishing IDPs: " + c.messages();
        });
        tc("UC#224", "Log In", () -> {
            final String id = "UC#224";
            Resp ok = rawLogin(CUST_EMAIL, pwFor(CUST_EMAIL));
            check(ok.status == 200, "expected HTTP 200, got " + ok.brief());
            msg(ok, in(id, "msg_success"));
            check(CUST_EMAIL.equalsIgnoreCase(ok.text("data", "user", "email")), "wrong user in the session");
            String em = ue("susplogin");   // 6a
            check(adminCreate(admin, em, STRONG, STRONG, "customer").status == 201, "could not create helper account");
            Resp st = json("PATCH", "/users/" + accountId(admin, em), admin, "{\"status\":\"suspended\"}");
            check(st.status == 200, "could not suspend helper account: " + st.brief());
            Resp sus = rawLogin(em, STRONG);
            check(sus.status == 403, "6a: expected HTTP 403, got " + sus.brief());
            msg(sus, in(id, "msg_suspended"));
            Resp wrong = rawLogin(CUST_EMAIL, "WrongPass!999");   // 6b
            check(wrong.status == 401, "6b: expected HTTP 401, got " + wrong.brief());
            msg(wrong, in(id, "msg_wrong"));
            return null;
        });
        tc("UC#225", "Log Out", () -> {
            final String id = "UC#225";
            Sess s = login(CUST_EMAIL, pwFor(CUST_EMAIL));
            Resp out = send("DELETE", "/auth/session", s, null, null);
            check(out.status == 200, "logout failed: " + out.brief());
            msg(out, in(id, "msg_success"));
            check("login".equals(out.text("data", "redirect")), "step 4: user is not sent back to the login page (redirect=" + out.text("data", "redirect") + ")");
            check(get("/navigation", s).status == 401, "session still active after logout");
            return null;
        });
        tc("UC#226", "Role-Based Screens", () -> {
            Map<String, Set<String>> nav = new LinkedHashMap<>();
            Object[][] roles = {{"customer", S_CUST}, {"designer", des}, {"admin", admin}, {"manager", mgr}};
            for (Object[] ro : roles) {
                Resp r = get("/navigation", (Sess) ro[1]);
                check(r.status == 200, ro[0] + ": menu request failed: " + r.brief());
                Object sc = r.at("data", "screens");
                check(sc instanceof List && !((List<?>) sc).isEmpty(), ro[0] + ": no menu items returned");
                Set<String> set = new HashSet<>();
                for (Object o : (List<?>) sc) set.add(String.valueOf(o));
                nav.put((String) ro[0], set);
            }
            for (String a : nav.keySet()) for (String b : nav.keySet())
                if (a.compareTo(b) < 0) check(!nav.get(a).equals(nav.get(b)), a + " and " + b + " get identical menus");
            check(get("/navigation", null).status == 401, "menu must need a login");
            check(get("/users", S_CUST).status == 403, "customer can open account management");
            check(get("/reports/options", S_CUST).status == 403, "customer can open reports");
            check(json("POST", "/categories", des, "{\"name\":\"x\",\"description\":\"\"}").status == 403, "designer can manage categories");
            check(get("/users", mgr).status == 403, "manager can open account management");
            check(get("/reports/options", admin).status == 403, "admin can open reports");
            return nav.toString();
        });
        tc("UC#227", "Search Accounts", () -> {
            final String id = "UC#227";
            check(get("/users", null).status == 401, "pre-condition: account list must need a login");
            Resp all = get("/users", admin);
            check(all.status == 200 && !((List<?>) all.at("data", "accounts")).isEmpty(), "account list empty: " + all.brief());
            Resp f = get("/users?q=" + enc(CUST_EMAIL), admin);
            check(f.status == 200 && account(admin, CUST_EMAIL) != null, "keyword search did not find " + CUST_EMAIL);
            check(((List<?>) f.at("data", "accounts")).size() < ((List<?>) all.at("data", "accounts")).size(), "search did not narrow the list");
            Resp none = get("/users?q=" + enc(in(id, "no_match_keyword")), admin);   // 5a
            check(none.status == 200 && ((List<?>) none.at("data", "accounts")).isEmpty(), "5a: expected an empty list");
            msg(none, in(id, "msg_no_match"));
            return null;
        });
        tc("UC#228", "Create Account for Any Role", () -> {
            final String id = "UC#228";
            check("createAccount".equals(get("/users/form", admin).text("data", "form")), "step 2: account form not shown");
            StringBuilder note = new StringBuilder();
            for (String role : in(id, "roles").split(",")) {
                String em = ue("role-" + role);
                Resp r = adminCreate(admin, em, in(id, "password"), in(id, "password"), role);
                check(r.status == 201, role + ": expected HTTP 201, got " + r.brief());
                msg(r, in(id, "msg_success"));
                Map<?, ?> a = account(admin, em);
                check(a != null && role.equals(a.get("role")), role + ": account saved with wrong role: " + a);
                Resp lg = rawLogin(em, in(id, "password"));
                if (lg.status == 200) check(role.equals(lg.text("data", "user", "role")), role + ": logs in with another role");
                else note.append(role).append(" cannot log in (").append(lg.status).append(") ");
                check(!note.toString().contains("cannot") || role.equals("designer"), role + " account created by the admin cannot log in: " + lg.brief());
            }
            String em = ue("mismatch");   // 5a
            Resp mm = adminCreate(admin, em, in(id, "password"), "Other!Pass9", "customer");
            check(mm.status == 400, "5a: expected HTTP 400, got " + mm.brief());
            msg(mm, in(id, "msg_mismatch"));
            check(account(admin, em) == null, "5a: account created despite mismatching passwords");
            Resp dup = adminCreate(admin, ue("role-customer"), in(id, "password"), in(id, "password"), "customer");   // 5b
            check(dup.status == 409, "5b: expected HTTP 409, got " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            return note.length() == 0 ? null : note.toString();
        });
        tc("UC#229", "Suspend or Reactivate Account", () -> {
            final String id = "UC#229";
            String em = ue("susp");
            check(adminCreate(admin, em, STRONG, STRONG, "customer").status == 201, "could not create helper account");
            String uid = accountId(admin, em);
            Resp s = json("PATCH", "/users/" + uid, admin, "{\"status\":\"suspended\"}");
            check(s.status == 200, "suspend failed: " + s.brief());
            msg(s, in(id, "msg_success"));
            check(Boolean.TRUE.equals(s.at("data", "refresh")), "step 5: list is not refreshed");
            check("suspended".equals(accountStatus(admin, em)), "status not saved as suspended");
            check(rawLogin(em, STRONG).status == 403, "suspended account can still log in");
            Resp a = json("PATCH", "/users/" + uid, admin, "{\"status\":\"active\"}");
            check(a.status == 200, "reactivate failed: " + a.brief());
            msg(a, in(id, "msg_success"));
            check("active".equals(accountStatus(admin, em)) && rawLogin(em, STRONG).status == 200, "account not usable after reactivation");
            Resp own = json("PATCH", "/users/" + adminId, admin, "{\"status\":\"suspended\"}");   // 4a
            check(own.status == 409, "4a: expected HTTP 409, got " + own.brief());
            msg(own, in(id, "msg_own"));
            check(get("/users", admin).status == 200, "admin was locked out of own account");
            return null;
        });
        tc("UC#230", "Delete Account", () -> {
            final String id = "UC#230";
            String em = ue("del");
            check(adminCreate(admin, em, STRONG, STRONG, "customer").status == 201, "could not create helper account");
            String uid = accountId(admin, em);
            Resp c = get("/users/" + uid + "/deletion-confirmation", admin);   // step 3
            check(c.status == 200 && Boolean.TRUE.equals(c.at("data", "confirmationRequired")), "step 3: no confirmation asked: " + c.body);
            Resp cancel = send("POST", "/users/" + uid + "/deletion-cancellations", admin, null, null);   // step 4
            check(cancel.status == 200, "cancel failed: " + cancel.brief());
            check(account(admin, em) != null, "account deleted although the admin cancelled");
            Resp own = send("DELETE", "/users/" + adminId, admin, null, null);   // 4a
            check(own.status == 409, "4a: expected HTTP 409, got " + own.brief());
            msg(own, in(id, "msg_own"));
            check(account(admin, "admin@meow.demo") != null, "4a: own account was deleted");
            Resp del = send("DELETE", "/users/" + uid, admin, null, null);   // 4b
            check(del.status == 200, "4b: delete failed: " + del.brief());
            msg(del, in(id, "msg_success"));
            check(account(admin, em) == null, "4b: account still listed");
            check(rawLogin(em, STRONG).status == 401, "4b: deleted account can still log in");
            return null;
        });

        // company + verification
        tc("UC#231", "Edit Company Details", () -> {
            final String id = "UC#231";
            Resp form = get("/me/company", des);
            check(form.status == 200 && CO_NAME.equals(form.text("data", "company", "name")), "step 2: form not filled with current details: " + form.brief());
            String uen = form.text("data", "company", "uen");
            int year = Integer.parseInt(in(id, "year"));
            String text = CO_DESC + " (edited by UC#231)";
            Resp r = json("PUT", "/me/company", des, "{\"name\":" + q(CO_NAME) + ",\"description\":" + q(text) + ",\"uen\":" + q(uen) + ",\"year\":" + year + "}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            msg(r, in(id, "msg_success"));
            check(Boolean.TRUE.equals(r.at("data", "refresh")), "step 7: page not refreshed");
            Resp back = get("/me/company", des);
            check(text.equals(back.text("data", "company", "description")) && n(back.at("data", "company", "year")) == year, "changes not saved: " + back.body);
            String otherUen = "UEN-" + USER_BY_EMAIL.get(EMPTY_EMAIL)[0];   // 5a: another company's UEN
            Resp dup = json("PUT", "/me/company", des, "{\"name\":" + q(CO_NAME) + ",\"description\":" + q(text) + ",\"uen\":" + q(otherUen) + ",\"year\":" + year + "}");
            check(dup.status == 409, "5a: expected HTTP 409, got " + dup.brief());
            msg(dup, in(id, "msg_dup_uen"));
            check(uen.equals(get("/me/company", des).text("data", "company", "uen")), "5a: UEN changed despite the error");
            json("PUT", "/me/company", des, "{\"name\":" + q(CO_NAME) + ",\"description\":" + q(CO_DESC) + ",\"uen\":" + q(uen) + ",\"year\":2015}");   // restore
            return null;
        });
        tc("UC#232", "Public Company Profile", () -> {
            final String id = "UC#232";
            Resp r = get("/designers/" + DES_ID + "/company", null);   // pre-condition: none (no login)
            check(r.status == 200 && CO_NAME.equals(r.text("data", "company", "name")), "public profile not shown: " + r.brief());
            Resp nf = get("/designers/" + in(id, "unknown_id") + "/company", null);   // 3a
            check(nf.status == 404, "3a: expected HTTP 404, got " + nf.brief());
            msg(nf, in(id, "msg_not_found"));
            check("home".equals(nf.text("data", "redirect")), "3a: user is not sent to the home page (redirect=" + nf.text("data", "redirect") + ")");
            return null;
        });
        tc("UC#256", "Approve or Reject Designer", () -> {
            final String id = "UC#256";
            String em1 = ue("approve"), em2 = ue("reject");
            Resp r1 = registerDesigner(em1, STRONG, "UC Approve " + RUN, "UEN-UC256A-" + RUN);
            Resp r2 = registerDesigner(em2, STRONG, "UC Reject " + RUN, "UEN-UC256B-" + RUN);
            check(r1.status == 201 && r2.status == 201, "could not register applicants");
            String id1 = r1.text("data", "id"), id2 = r2.text("data", "id");
            Resp page = get("/users/" + id1 + "/verification", admin);   // steps 1-2
            check(page.status == 200 && "pending".equals(page.text("data", "user", "status")), "step 2: application not shown as pending: " + page.body);
            Resp ap = json("PUT", "/users/" + id1 + "/verification", admin, "{\"decision\":\"approve\"}");
            check(ap.status == 200, "approve failed: " + ap.brief());
            msg(ap, in(id, "msg_approved"));
            check(Boolean.TRUE.equals(ap.at("data", "refreshQueue")), "step 7: review queue not refreshed");
            check("active".equals(accountStatus(admin, em1)), "step 4: status not updated to active");
            Sess ds = login(em1, STRONG);   // step 5: may publish now
            String p = createProject(ds, "UC256 IDP " + RUN, "d", "d", "Current");
            check(publish(ds, p).status == 200, "step 5: approved designer cannot publish");
            Resp rj = json("PUT", "/users/" + id2 + "/verification", admin, "{\"decision\":\"reject\"}");   // 3a
            check(rj.status == 200, "3a: reject failed: " + rj.brief());
            msg(rj, in(id, "msg_rejected"));
            check("rejected".equals(accountStatus(admin, em2)), "3a: status not updated to rejected");
            check(rawLogin(em2, STRONG).status == 403, "3a: rejected designer can still log in");
            String np = in(id, "no_proof_designer");   // 3b: applicant without business proof
            Map<?, ?> acct = account(admin, np);
            if (acct == null) na("seeded applicant '" + np + "' (a pending designer with no proof) not found - needs the --meow.seed-demo=true data");
            Resp noProof = json("PUT", "/users/" + acct.get("id") + "/verification", admin, "{\"decision\":\"approve\"}");
            check(noProof.status == 409, "3b: expected HTTP 409, got " + noProof.brief());
            msg(noProof, in(id, "msg_no_proof"));
            check(!"active".equals(accountStatus(admin, np)), "3b: applicant without proof was approved");
            return null;
        });

        // IDPs (designer)
        tc("UC#233", "Create IDP", () -> {
            final String id = "UC#233";
            Resp r = json("POST", "/projects", des, "{\"title\":" + q("UC233 IDP " + RUN) + ",\"description\":\"Created by UC#233\",\"projectDetails\":\"Details\",\"status\":\"Current\"}");
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Map<String, Object> m = findMine(des, r.text("data", "id"));
            check(m != null && ("UC233 IDP " + RUN).equals(m.get("title")) && "Current".equals(m.get("stage")), "new IDP not saved correctly: " + m);
            String em = ue("unverified");   // pre-condition: verified designer only
            check(registerDesigner(em, STRONG, "UC Unverified " + RUN, "UEN-UC233-" + RUN).status == 201, "could not register applicant");
            Resp blocked = json("POST", "/projects", login(em, STRONG), "{\"title\":\"x\",\"description\":\"d\",\"projectDetails\":\"x\",\"status\":\"Current\"}");
            check(blocked.status == 403, "pre-condition: unverified designer could create an IDP: " + blocked.brief());
            return null;
        });
        tc("UC#234", "Upload Photos and Choose Cover", () -> {
            final String id = "UC#234";
            String p = createProject(des, "UC234 Photos " + RUN, "d", "d", "Current");
            int count = Integer.parseInt(in(id, "photos"));
            String[][] pngs = new String[count][];
            for (int i = 0; i < count; i++) pngs[i] = new String[]{"room" + i + ".png", "image/png", "png"};
            Resp r = photos(des, p, in(id, "cover"), pngs);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Map<String, Object> m = findMine(des, p);
            check(((List<?>) m.get("photos")).size() == count, "expected " + count + " saved photos, found " + ((List<?>) m.get("photos")).size());
            check(n(m.get("cover")) == Integer.parseInt(in(id, "cover")), "step 3: cover not set to the chosen photo (cover=" + m.get("cover") + ")");
            Resp bad = photos(des, p, in(id, "bad_cover"), pngs);   // 3a
            check(bad.status == 400, "3a: expected HTTP 400, got " + bad.brief());
            msg(bad, in(id, "msg_cover"));
            Resp exe = photos(des, p, "0", new String[][]{{"floorplan.exe", "application/octet-stream", "exe"}});   // 3b
            check(exe.status == 400, "3b: expected HTTP 400, got " + exe.brief());
            msg(exe, in(id, "msg_type"));
            check(((List<?>) findMine(des, p).get("photos")).size() == count, "3b: photo list changed by the rejected upload");
            return null;
        });
        tc("UC#235", "Edit IDP", () -> {
            final String id = "UC#235";
            String p = createProject(des, "UC235 Original " + RUN, "before", "d", "Current");
            Resp r = json("PATCH", "/projects/" + p, des, "{\"title\":" + q("UC235 Edited " + RUN) + ",\"description\":\"after\",\"projectDetails\":\"d2\",\"status\":\"Past\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Map<String, Object> m = findMine(des, p);
            check(("UC235 Edited " + RUN).equals(m.get("title")) && "after".equals(m.get("description")) && "Past".equals(m.get("stage")), "changes not saved: " + m);
            return null;
        });
        tc("UC#236", "View Own IDP List", () -> {
            Resp r = get("/projects?mine=true", des);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(listHas(r, "idps", PID0) && listHas(r, "idps", PID1), "own IDPs missing from the list");
            for (Object o : (List<?>) r.at("data", "idps"))
                check(DES_ID.equals(((Map<?, ?>) o).get("owner")), "list contains another designer's IDP: " + ((Map<?, ?>) o).get("title"));
            check(!listHas(get("/projects?mine=true", desEmpty), "idps", PID1), "another designer sees this designer's IDP");
            return ((List<?>) r.at("data", "idps")).size() + " own IDP(s) listed";
        });
        tc("UC#263", "Publish or Unpublish IDP", () -> {
            final String id = "UC#263";
            String word = "ucpub" + RUN;
            String p = createProject(des, "UC263 " + RUN, "about " + word, "d", "Current");
            check(get("/projects/" + p, S_CUST).status == 404, "unpublished IDP is visible to customers");
            Resp pub = json("PUT", "/projects/" + p + "/publication", des, "{\"action\":\"Publish\"}");
            check(pub.status == 200, "publish failed: " + pub.brief());
            msg(pub, in(id, "msg_publish"));
            check(get("/projects/" + p, S_CUST).status == 200, "published IDP not visible to customers");
            check(listHas(get("/search-results?q=" + word, S_CUST), "idps", p), "published IDP not found by search");
            check(Boolean.TRUE.equals(findMine(des, p).get("published")), "portfolio does not show it as published");
            Resp un = json("PUT", "/projects/" + p + "/publication", des, "{\"action\":\"Unpublish\"}");   // 3a
            check(un.status == 200, "unpublish failed: " + un.brief());
            msg(un, in(id, "msg_unpublish"));
            check(get("/projects/" + p, S_CUST).status == 404, "unpublished IDP still visible to customers");
            check(!listHas(get("/search-results?q=" + word, S_CUST), "idps", p), "unpublished IDP still found by search");
            return null;
        });
        tc("UC#266", "Assign IDP Categories", () -> {
            final String id = "UC#266";
            String p = createProject(des, "UC266 " + RUN, "d", "d", "Current");
            Resp avail = get("/categories/available", des);
            List<?> cats = (List<?>) avail.at("data");
            check(avail.status == 200 && cats != null && !cats.isEmpty(), "no categories offered");
            Map<?, ?> c1 = (Map<?, ?>) cats.get(0), c2 = (Map<?, ?>) cats.get(cats.size() - 1);
            Resp r = json("PUT", "/projects/" + p + "/categories", des, "{\"categories\":[" + q(String.valueOf(c1.get("id"))) + "," + q(String.valueOf(c2.get("id"))) + "]}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            msg(r, in(id, "msg_success"));
            String saved = String.valueOf(findMine(des, p));
            check(saved.contains(String.valueOf(c1.get("id"))) || saved.contains(String.valueOf(c1.get("name"))), "chosen category not saved on the IDP: " + saved);
            Resp none = json("PUT", "/projects/" + p + "/categories", des, "{\"categories\":[]}");   // 2a
            check(none.status == 400, "2a: expected HTTP 400, got " + none.brief());
            msg(none, in(id, "msg_none"));
            Resp inv = json("PUT", "/projects/" + p + "/categories", des, "{\"categories\":[" + q(in(id, "invalid_category")) + "]}");   // 2b
            check(inv.status == 400, "2b: expected HTTP 400, got " + inv.brief());
            msg(inv, in(id, "msg_invalid"));
            return null;
        });

        // customer
        tc("UC#237", "Search Published IDPs", () -> {
            String word = "ucsearch" + RUN;
            String pub = createProject(des, "UC237 published " + RUN, "a " + word + " apartment", "d", "Current");
            String draft = createProject(des, "UC237 draft " + RUN, "a " + word + " draft", "d", "Current");
            check(publish(des, pub).status == 200, "publish failed");
            Resp r = get("/search-results?q=" + word, S_CUST);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(listHas(r, "idps", pub), "published matching IDP not found");
            check(!listHas(r, "idps", draft), "unpublished IDP leaked into the search results");
            return null;
        });
        tc("UC#238", "View Search Results", () -> {
            final String id = "UC#238";
            Resp r = get("/search-results?q=" + enc(head(in("TC4.1", "keyword"))), S_CUST);
            List<?> list = (List<?>) r.at("data", "idps");
            check(r.status == 200 && !list.isEmpty(), "expected a list of matching IDPs: " + r.brief());
            for (Object o : list) {
                Map<?, ?> m = (Map<?, ?>) o;
                check(m.get("title") != null && m.get("stage") != null, "result card lacks title/stage: " + m);
            }
            Resp none = get("/search-results?q=" + enc(in(id, "no_match_keyword")), S_CUST);   // 3a
            check(none.status == 200 && ((List<?>) none.at("data", "idps")).isEmpty(), "3a: expected an empty list");
            msg(none, in(id, "msg_no_match"));
            return list.size() + " result(s) for the keyword";
        });
        tc("UC#239", "View IDP Details and Gallery", () -> {
            final String id = "UC#239";
            Resp r = get("/projects/" + KW, S_CUST);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("TC4.1 Keyword IDP".equals(r.text("data", "idp", "title")), "step 4: details not shown");
            check(r.at("data", "gallery") instanceof List && ((List<?>) r.at("data", "gallery")).size() == 2, "step 5: photo gallery missing / wrong size: " + r.at("data", "gallery"));
            Resp nf = get("/projects/" + in(id, "unknown_id"), S_CUST);   // 3a
            check(nf.status == 404, "3a: expected HTTP 404, got " + nf.brief());
            msg(nf, in(id, "msg_not_found"));
            return null;
        });
        tc("UC#240", "Company Link from IDP", () -> {
            final String id = "UC#240";
            Resp r = get("/projects/" + KW + "/company", S_CUST);
            check(r.status == 200 && CO_NAME.equals(r.text("data", "company", "name")), "company profile not shown: " + r.brief());
            Resp nf = get("/projects/" + in(id, "unknown_id") + "/company", S_CUST);   // 3a
            check(nf.status == 404, "3a: expected HTTP 404, got " + nf.brief());
            msg(nf, in(id, "msg_not_found"));
            return null;
        });
        tc("UC#242", "Save Favourite without Duplicates", () -> {
            final String id = "UC#242";
            Sess c = newCustomer("fav");
            Resp r = send("PUT", "/me/favourites/" + KW, c, null, null);
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            msg(r, in(id, "msg_success"));
            Resp dup = send("PUT", "/me/favourites/" + KW, c, null, null);   // 3a
            check(dup.status == 200 || dup.status == 409, "3a: unexpected " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            String idsList = ids(get("/me/favourites", c), "idps");
            check(idsList.equals(KW + ","), "favourites should contain the IDP exactly once, got: " + idsList);
            return null;
        });
        tc("UC#273", "View Favourites List", () -> {
            Sess c = newCustomer("favlist");
            Resp empty = get("/me/favourites", c);
            check(empty.status == 200 && ((List<?>) empty.at("data", "idps")).isEmpty(), "a new customer should have no favourites: " + empty.brief());
            check(send("PUT", "/me/favourites/" + KW, c, null, null).status == 201, "could not save a favourite");
            check(send("PUT", "/me/favourites/" + PID1, c, null, null).status == 201, "could not save a second favourite");
            Resp r = get("/me/favourites", c);
            check(r.status == 200 && listHas(r, "idps", KW) && listHas(r, "idps", PID1) && ((List<?>) r.at("data", "idps")).size() == 2, "favourites list wrong: " + ids(r, "idps"));
            return null;
        });
        tc("UC#241", "Record IDP View", () -> {
            Sess c = newCustomer("view");
            int[] before = stats(des, KW);
            Resp r = recordView(c, KW);   // steps 1-5
            check(r.status == 200, "view request failed: " + r.brief());
            int[] after = stats(des, KW);
            check(after[0] == before[0] + 1, "view count " + before[0] + " -> " + after[0] + " (expected +1)");
            check(n(r.at("data", "views")) == after[0], "step 5: view count shown (" + r.at("data", "views") + ") differs from the stored count " + after[0]);
            String draft = createProject(des, "UC241 draft " + RUN, "d", "d", "Current");   // step 2: only published IDPs
            recordView(c, draft);
            check(stats(des, draft)[0] == 0, "a view of an unpublished IDP was recorded");
            return null;
        });
        tc("UC#243", "Record IDP Shortlist", () -> {
            Sess c = newCustomer("short");
            check(send("PUT", "/me/favourites/" + KW, c, null, null).status == 201, "could not save the IDP to the shortlist");
            int before = stats(des, KW)[1];
            Resp r = shortlist(c, KW);
            check(r.status == 200, "shortlist request failed: " + r.brief());
            int after = stats(des, KW)[1];
            check(after == before + 1, "shortlist count " + before + " -> " + after + " (expected +1)");
            check(n(r.at("data", "shortlists")) == after, "step 5: shown count (" + r.at("data", "shortlists") + ") differs from stored " + after);
            Resp dup = shortlist(c, KW);   // 3a
            check(stats(des, KW)[1] == after, "3a: a duplicate shortlist was counted again");
            check(dup.messages().toLowerCase().contains("shortlist"), "3a: no note that it is already shortlisted, got: '" + dup.messages() + "'");
            return "3a note: " + dup.messages();
        });

        // designer analytics
        tc("UC#244", "Portfolio Totals", () -> {
            Resp t = get("/me/analytics/totals", des);
            check(t.status == 200, "expected HTTP 200, got " + t.brief());
            Resp per = get("/me/analytics/projects", des);
            int v = 0, s = 0;
            for (Object o : (List<?>) per.at("data", "projects")) { v += n(((Map<?, ?>) o).get("views")); s += n(((Map<?, ?>) o).get("shortlists")); }
            check(n(t.at("data", "totalViews")) == v && n(t.at("data", "totalShortlists")) == s,
                    "totals " + t.at("data", "totalViews") + "/" + t.at("data", "totalShortlists") + " differ from the sum over the IDPs " + v + "/" + s);
            check(v > 0 && s > 0, "totals should include the activity recorded earlier in this run");
            Resp z = get("/me/analytics/totals", desEmpty);
            check(n(z.at("data", "totalViews")) == 0 && n(z.at("data", "totalShortlists")) == 0, "designer without IDPs should have 0/0: " + z.body);
            return "views=" + v + ", shortlists=" + s;
        });
        tc("UC#245", "Stats per IDP", () -> {
            Resp per = get("/me/analytics/projects", des);
            check(per.status == 200, "expected HTTP 200, got " + per.brief());
            Resp mine = get("/projects?mine=true", des);
            for (Object o : (List<?>) mine.at("data", "idps"))
                check(listHas(per, "projects", String.valueOf(((Map<?, ?>) o).get("id"))), "IDP '" + ((Map<?, ?>) o).get("title") + "' missing from the per-IDP statistics");
            int[] a = stats(des, KW), other = stats(des, PID1);
            recordView(newCustomer("stat"), KW);
            int[] b = stats(des, KW), other2 = stats(des, PID1);
            check(b[0] == a[0] + 1, "statistics of the viewed IDP did not change");
            check(other[0] == other2[0] && other[1] == other2[1], "statistics of a different IDP changed");
            return null;
        });

        // platform manager
        tc("UC#246", "Create Category", () -> {
            final String id = "UC#246";
            check(get("/categories/form", mgr).status == 200, "step 2: category form not shown");
            String name = in(id, "name") + " " + RUN;
            Resp r = json("POST", "/categories", mgr, "{\"name\":" + q(name) + ",\"description\":" + q(in(id, "description")) + "}");
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            msg(r, in(id, "msg_success"));
            ucCat[0] = r.text("data", "id");
            ucCat[1] = name;
            check(get("/categories?flow=edit", mgr).body.contains(name), "new category not in the category list");
            check(get("/categories/available", des).body.contains(name), "new category not offered to designers");
            Resp empty = json("POST", "/categories", mgr, "{\"name\":\"\",\"description\":\"x\"}");   // 4a
            check(empty.status == 400, "4a: expected HTTP 400, got " + empty.brief());
            msg(empty, in(id, "msg_empty"));
            Resp dup = json("POST", "/categories", mgr, "{\"name\":" + q(name.toLowerCase()) + ",\"description\":\"x\"}");   // 4b (case-insensitive)
            check(dup.status == 409, "4b: expected HTTP 409, got " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            return null;
        });
        tc("UC#247", "Edit Category", () -> {
            final String id = "UC#247";
            if (ucCat[0] == null) na("needs the category created in UC#246");
            Resp list = get("/categories?flow=edit", mgr);
            check(list.status == 200 && list.body.contains(ucCat[1]), "steps 2-3: category list not shown");
            Resp form = get("/categories/" + ucCat[0] + "/edit-form", mgr);
            check(form.status == 200 && ucCat[1].equals(form.text("data", "category", "name")), "step 5: edit form not filled with the current values: " + form.body);
            String renamed = ucCat[1] + " Edited";
            Resp r = json("PATCH", "/categories/" + ucCat[0], mgr, "{\"name\":" + q(renamed) + ",\"description\":\"changed\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            msg(r, in(id, "msg_success"));
            check(get("/categories?flow=edit", mgr).body.contains(renamed), "new name not in the list");
            ucCat[1] = renamed;
            Resp empty = json("PATCH", "/categories/" + ucCat[0], mgr, "{\"name\":\"\",\"description\":\"x\"}");   // 7a
            check(empty.status == 400, "7a: expected HTTP 400, got " + empty.brief());
            msg(empty, in(id, "msg_empty"));
            String other = null;   // 7b: name of another existing category
            for (Object o : (List<?>) list.at("data", "categories"))
                if (!ucCat[0].equals(String.valueOf(((Map<?, ?>) o).get("id")))) { other = String.valueOf(((Map<?, ?>) o).get("name")); break; }
            check(other != null, "no other category to test a duplicate name with");
            Resp dup = json("PATCH", "/categories/" + ucCat[0], mgr, "{\"name\":" + q(other) + ",\"description\":\"x\"}");
            check(dup.status == 409, "7b: expected HTTP 409, got " + dup.brief());
            msg(dup, in(id, "msg_dup"));
            check(get("/categories?flow=edit", mgr).body.contains(renamed), "7b: category name changed despite the error");
            return null;
        });
        tc("UC#248", "Make Category Available", () -> {
            final String id = "UC#248";
            if (ucCat[0] == null) na("needs the category created in UC#246");
            check(get("/categories/available", des).body.contains(ucCat[0]), "category should start out available");
            Resp off = json("PUT", "/categories/" + ucCat[0] + "/availability", mgr, "{\"available\":false}");
            check(off.status == 200, "switch off failed: " + off.brief());
            msg(off, in(id, "msg_off"));
            check(!get("/categories/available", des).body.contains(ucCat[0]), "unavailable category still offered when creating IDPs");
            Resp on = json("PUT", "/categories/" + ucCat[0] + "/availability", mgr, "{\"available\":true}");   // 6a
            check(on.status == 200, "switch on failed: " + on.brief());
            msg(on, in(id, "msg_on"));
            check(get("/categories/available", des).body.contains(ucCat[0]), "category not offered again after switching on");
            return null;
        });

        // reports
        tc("UC#249", "Select Report Period", () -> {
            final String id = "UC#249";
            Resp opts = get("/reports/options", mgr);
            check(opts.status == 200, "step 2: options not shown: " + opts.brief());
            for (String p : in(id, "periods").split(",")) check(opts.body.contains(p), "period option '" + p + "' missing: " + opts.body);
            LocalDate day = LocalDate.now(SGT).minusDays(9);
            LocalDate monday = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            Object[][] exp = {
                    {"daily", day, day}, {"weekly", monday, monday.plusDays(6)},
                    {"monthly", day.withDayOfMonth(1), day.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())}};
            for (Object[] e : exp) {
                Resp r = json("POST", "/reports/period", mgr, "{\"period\":\"" + e[0] + "\",\"date\":\"" + day + "\"}");
                check(r.status == 200, e[0] + ": expected HTTP 200, got " + r.brief());
                check(String.valueOf(e[1]).equals(r.text("data", "from")) && String.valueOf(e[2]).equals(r.text("data", "to")),
                        e[0] + " period for " + day + ": expected " + e[1] + ".." + e[2] + ", got " + r.text("data", "from") + ".." + r.text("data", "to"));
            }
            Resp td = json("POST", "/reports/period", mgr, "{\"period\":\"daily\",\"date\":\"" + TODAY + "\"}");
            check(td.status == 200, "today must be accepted: " + td.brief());
            Resp fut = json("POST", "/reports/period", mgr, "{\"period\":\"daily\",\"date\":\"" + LocalDate.now(SGT).plusDays(1) + "\"}");   // 5a
            check(fut.status == 400, "5a: expected HTTP 400, got " + fut.brief());
            msg(fut, in(id, "msg_future"));
            return null;
        });
        tc("UC#250", "Generate Activity Totals", () -> {
            final String id = "UC#250";
            int[] before = reportTotals(mgr, TODAY);
            Sess c = newCustomer("activity");                                   // +1 new user
            String p = createProject(des, "UC250 activity " + RUN, "d", "d", "Current");
            check(publish(des, p).status == 200, "publish failed");             // +1 new project
            check(recordView(c, p).status == 200, "view failed");               // +1 view
            check(send("PUT", "/me/favourites/" + p, c, null, null).status == 201, "save failed");
            check(shortlist(c, p).status == 200, "shortlist failed");           // +1 shortlist
            Resp gen = get("/reports/activity?period=daily&date=" + TODAY, mgr);
            check(gen.status == 200, "expected HTTP 200, got " + gen.brief());
            int[] after = reportTotals(mgr, TODAY);
            check(after[0] - before[0] == 1 && after[1] - before[1] == 1 && after[2] - before[2] == 1 && after[3] - before[3] == 1,
                    "totals changed by users/projects/views/shortlists = " + (after[0] - before[0]) + "/" + (after[1] - before[1]) + "/" + (after[2] - before[2]) + "/" + (after[3] - before[3]) + ", expected 1/1/1/1");
            check(!gen.messages().contains(in(id, "msg_none")), "'no activity' shown although there is activity");
            int[] week = new int[4];
            Map<?, ?> wt = (Map<?, ?>) get("/reports/activity?period=weekly&date=" + TODAY, mgr).at("data", "totals");
            check(n(wt.get("view")) >= after[2] && n(wt.get("registration")) >= after[0], "weekly totals are smaller than today's totals");
            Resp none = get("/reports/activity?period=daily&date=" + in(id, "empty_date"), mgr);   // 8a
            check(none.status == 200, "8a: expected HTTP 200, got " + none.brief());
            msg(none, in(id, "msg_none"));
            int[] z = reportTotals(mgr, in(id, "empty_date"));
            check(z[0] + z[1] + z[2] + z[3] == 0, "8a: totals should all be 0, got " + java.util.Arrays.toString(z));
            Resp fut = get("/reports/activity?period=daily&date=" + LocalDate.now(SGT).plusDays(1), mgr);
            check(fut.status == 400, "a future date must be rejected, got " + fut.brief());
            return "today's change: users +1, projects +1, views +1, shortlists +1";
        });
        tc("UC#251", "View Daily Report Table", () -> {
            final String id = "UC#251";
            Resp d = get("/reports/table?period=daily&date=" + TODAY, mgr);
            check(d.status == 200, "expected HTTP 200, got " + d.brief());
            List<?> dr = (List<?>) d.at("data", "rows");
            check(dr.size() == 2 && TODAY.equals(((Map<?, ?>) dr.get(0)).get("date")) && "Total".equals(((Map<?, ?>) dr.get(1)).get("date")),
                    "daily table should have one row for the date plus a Total row: " + dr);
            Resp w = get("/reports/table?period=weekly&date=" + TODAY, mgr);
            List<?> rows = (List<?>) w.at("data", "rows");
            check(w.status == 200 && rows.size() >= 2, "weekly table not shown: " + w.brief());
            Map<?, ?> total = (Map<?, ?>) rows.get(rows.size() - 1);
            check("Total".equals(total.get("date")), "step 5: no Total row");
            String[] cols = {"newUsers", "newIDPs", "views", "shortlists"};
            int[] sum = new int[4];
            Set<Object> dates = new HashSet<>();
            String prev = "";
            for (int i = 0; i < rows.size() - 1; i++) {
                Map<?, ?> row = (Map<?, ?>) rows.get(i);
                String dt = String.valueOf(row.get("date"));
                check(dates.add(dt) && dt.compareTo(prev) > 0, "dates must be unique and in order, got " + dt + " after " + prev);
                prev = dt;
                for (int k = 0; k < 4; k++) sum[k] += n(row.get(cols[k]));
            }
            for (int k = 0; k < 4; k++) check(n(total.get(cols[k])) == sum[k], "Total row " + cols[k] + "=" + total.get(cols[k]) + " but the rows add up to " + sum[k]);
            check(dates.contains(TODAY), "today's row is missing from the weekly table");
            Resp none = get("/reports/table?period=daily&date=" + in(id, "empty_date"), mgr);   // 5a
            check(none.status == 200, "5a: expected HTTP 200, got " + none.brief());
            msg(none, in(id, "msg_none"));
            return rows.size() - 1 + " daily row(s) + Total";
        });
    }

    static boolean waitForApp() throws Exception {
        for (int i = 0; i < 90; i++) {
            try {
                if (get("/health", null).status == 200) return true;
            } catch (Exception ignored) { }
            Thread.sleep(1000);
        }
        return false;
    }

    static void summary() throws Exception {
        int pass = 0, fail = 0, na = 0;
        StringBuilder csv = new StringBuilder("tc_id,result,scenario,detail\n");
        for (Result r : RESULTS) {
            if (r.status.equals("PASS")) pass++;
            else if (r.status.equals("FAIL")) fail++;
            else na++;
            csv.append(r.id).append(',').append(r.status).append(",\"").append(r.scenario.replace("\"", "'"))
                    .append("\",\"").append(r.detail.replace("\"", "'")).append("\"\n");
        }
        Files.write(Paths.get(DATA, "test_results.csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("------------------------------------------------------------------");
        System.out.printf("TOTAL %d   PASS %d   FAIL %d   N/A %d%n", RESULTS.size(), pass, fail, na);
        if (fail > 0) {
            System.out.println("FAILED:");
            for (Result r : RESULTS) if (r.status.equals("FAIL")) System.out.println("  " + r.id + "  " + r.scenario);
        }
        System.out.println("N/A = the app has no endpoint/field that the test case needs (not counted as a failure).");
        System.out.println("Details written to " + Paths.get(DATA, "test_results.csv"));
        System.out.println(fail == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        System.exit(fail == 0 ? 0 : 1);
    }

    // minimal JSON reader (no libraries needed)
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String text) {
            Json j = new Json(text);
            j.ws();
            Object v = j.value();
            j.ws();
            if (j.i != j.s.length()) throw new IllegalArgumentException("trailing data");
            return v;
        }

        static String quote(String v) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : v.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> { if (c < 32) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
                }
            }
            return sb.append('"').toString();
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private Object value() {
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++; ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws(); i++; // ':'
                    ws();
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++; ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    ws();
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IllegalArgumentException("bad json at " + i);
            return Double.valueOf(s.substring(st, i));
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }
    }
}