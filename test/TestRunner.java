import java.io.ByteArrayOutputStream;
import java.net.URI;
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
 * Executes the test cases described in testdata/test_case_data.csv (written by TestDataGenerator)
 * against the running MEOW Spring Boot application and prints PASS / FAIL / N/A for every case.
 *
 * Run (the app must already be running, started with --meow.seed-demo=true):
 *     java TestRunner.java                       (no compile step needed, Java 17+)
 *     java TestRunner.java http://127.0.0.1:8000 (optional base URL)
 *
 * Exit code: 0 = no FAIL, 1 = at least one FAIL, 2 = could not run (app down / data missing).
 */
public class TestRunner {

    // ------------------------------------------------------------------ config
    static String BASE = "http://127.0.0.1:8000";
    static final String DATA = "testdata";
    static final String DEMO_PASSWORD = "MEOWDemo!2026";
    static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    // 1x1 PNG
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    static final byte[] PDF = "%PDF-1.4 test proof document".getBytes(StandardCharsets.UTF_8);

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // ------------------------------------------------------------------ result model
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

    // ------------------------------------------------------------------ generated test data
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

    // ------------------------------------------------------------------ HTTP helpers
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

    // ------------------------------------------------------------------ bookkeeping for the activity report
    static int expUsers = 0, expPublished = 0, expViews = 0, expShortlists = 0;
    static final Set<String> publishedOnce = new HashSet<>();

    // ------------------------------------------------------------------ API actions
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
        for (int i = 0; i < count; i++) m.file("photos", "photo" + i + ".png", "image/png", PNG);
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

    // ================================================================== main
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
        String emptyEmail = head(in("TC3.9", "designer"));
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

        // ============================================================ TC1 - user accounts
        final String newEmail = in("TC1.1", "email"), newPw = in("TC1.1", "password");
        tc("TC1.1", "Register valid", () -> {
            Resp r = registerCustomer(newEmail, newPw);
            check(r.status == 201, "expected HTTP 201, got " + r.brief());
            login(newEmail, newPw);
            return "account created and can log in (redirect to homepage is a browser action, not testable via API)";
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

        // ============================================================ TC2 - company information
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
        tc("TC2.3", "Upload valid logo", () -> { na("the API has no logo-upload endpoint (companies.logo_id exists in the DB but nothing writes it)"); return null; });
        tc("TC2.4a", "Upload invalid logo (type)", () -> { na("the API has no logo-upload endpoint"); return null; });
        tc("TC2.4b", "Upload invalid logo (size)", () -> { na("the API has no logo-upload endpoint"); return null; });
        tc("TC2.5", "Edit company info", () -> {
            String newText = coDesc + " | " + in("TC2.5", "address");
            Resp r = json("PUT", "/me/company", fDes, "{\"name\":" + q(coName) + ",\"description\":" + q(newText) + ",\"uen\":\"UEN-IDS-001\",\"year\":2015}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            Resp pub = get("/designers/" + desId + "/company", fCust);
            check(newText.equals(pub.text("data", "company", "description")), "public page does not show the edit");
            return "company has no address field, so the address text was saved in the description";
        });
        tc("TC2.6", "Customer views company", () -> {
            Resp r = get("/designers/" + desId + "/company", fCust);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(coName.equals(r.text("data", "company", "name")), "company name missing on public page");
            String low = r.body.toLowerCase();
            for (String bad : new String[]{"proof", "password", "private", "internal"})
                check(!low.contains(bad), "public page exposes private data (found '" + bad + "')");
            return null;
        });

        // ============================================================ TC3 - IDP portfolio
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
        tc("TC3.3", "End date before start", () -> { na("IDPs have no start/end date fields in this app (only title, description, details, status)"); return null; });
        tc("TC3.4", "Missing title", () -> {
            Resp r = json("POST", "/projects", fDes, "{\"title\":\"\",\"description\":\"d\",\"projectDetails\":\"x\",\"status\":\"Current\"}");
            check(r.status == 400, "expected HTTP 400, got " + r.brief());
            return null;
        });
        tc("TC3.5", "Mark current as completed", () -> {
            Resp r = json("PATCH", "/projects/" + pid[0], fDes, "{\"title\":\"TC3.1 Current IDP\",\"description\":\"Added by the test runner\",\"projectDetails\":\"Details\",\"status\":\"Past\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("Past".equals(findMine(fDes, pid[0]).get("stage")), "IDP did not move to Past");
            return "app calls the completed state 'Past'";
        });
        tc("TC3.6", "Edit IDP details", () -> {
            Resp p = publish(fDes, pid[1]);
            check(p.status == 200, "could not publish IDP for the customer view: " + p.brief());
            Resp r = json("PATCH", "/projects/" + pid[1], fDes, "{\"title\":\"TC3.2 Past IDP\",\"description\":\"Updated text\",\"projectDetails\":\"Details\",\"status\":\"Past\"}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check("Updated text".equals(findMine(fDes, pid[1]).get("description")), "portfolio not updated");
            check("Updated text".equals(get("/projects/" + pid[1], fCust).text("data", "idp", "description")), "customer view not updated");
            return "budget is not an editable field in this app; description/title/status were tested";
        });
        tc("TC3.7", "Delete IDP", () -> { na("there is no delete-IDP endpoint (only publish/unpublish)"); return null; });
        tc("TC3.8", "Image limit", () -> {
            String id = createProject(fDes, "TC3.8 Image limit", "d", "d", "Current");
            Resp r = uploadPhotos(fDes, id, 6);
            check(r.status >= 400, "6 images accepted (HTTP " + r.status + "); spec limit is 5 - the app only limits to 20 per upload");
            return null;
        });
        tc("TC3.9", "Empty portfolio", () -> {
            Resp r = get("/projects?mine=true", fDesEmpty);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(((List<?>) r.at("data", "idps")).isEmpty(), "portfolio should be empty");
            check(!r.messages().isBlank(), "no friendly 'no projects yet' message returned (messages list is empty)");
            return "message: " + r.messages();
        });
        tc("TC3.10", "Filter by status", () -> { na("search has no status filter (GET /projects takes only q, mine, favourites)"); return null; });

        // ============================================================ TC4 - customer search
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
            Resp r = get("/projects?q=" + head(in("TC4.2", "keyword")), fCust);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(((List<?>) r.at("data", "idps")).isEmpty(), "expected no results");
            check(r.messages().toLowerCase().contains("no projects"), "expected a 'no results' message, got: " + r.messages());
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
        tc("TC4.6", "Remove saved IDP", () -> { na("there is no endpoint to remove a favourite (only PUT /me/favourites/{id})"); return null; });
        tc("TC4.7", "Persistence", () -> {
            Sess again = login(custEmail, pwFor(custEmail));
            check(ids(get("/me/favourites", again), "idps").contains(kw[0]), "saved IDP missing after logging in again");
            return null;
        });

        // ============================================================ TC5 - tracking
        tc("TC5.1", "View increments", () -> {
            int before = stats(fDes, kw[0])[0];
            Resp r = recordView(fCust, kw[0]);
            check(r.status == 200, "view request failed: " + r.brief());
            int after = stats(fDes, kw[0])[0];
            check(after == before + 1, "view count " + before + " -> " + after + " (expected +1)");
            return null;
        });
        tc("TC5.2", "Repeat view same session", () -> {
            int before = stats(fDes, kw[0])[0];
            recordView(fCust, kw[0]);
            int after = stats(fDes, kw[0])[0];
            check(after == before, "view count " + before + " -> " + after + ": every reopen is counted, no unique-per-session rule");
            return null;
        });
        tc("TC5.3", "Shortlist increments", () -> {
            int before = stats(fDes, kw[0])[1];
            Resp r = shortlist(fCust, kw[0]);
            check(r.status == 200, "shortlist request failed: " + r.brief());
            int after = stats(fDes, kw[0])[1];
            check(after == before + 1, "shortlist count " + before + " -> " + after + " (expected +1)");
            return null;
        });
        tc("TC5.4", "Unsave decrements", () -> { na("there is no unsave/remove-favourite endpoint, so the count can never decrease"); return null; });
        tc("TC5.5", "No activity", () -> {
            String id = createProject(fDes, "TC5.5 No activity", "d", "d", "Current");
            int[] s = stats(fDes, id);
            check(s[0] == 0 && s[1] == 0, "expected 0 views / 0 shortlists, got " + s[0] + " / " + s[1]);
            return null;
        });
        tc("TC5.6", "ID views own IDP", () -> {
            int before = stats(fDes, kw[0])[0];
            recordView(fDes, kw[0]);
            int after = stats(fDes, kw[0])[0];
            check(after == before, "view count " + before + " -> " + after + ": the designer's own view was counted");
            return null;
        });

        // ============================================================ TC6 - platform management
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
        tc("TC6.5", "Make category in use unavailable", () -> {
            Resp a = json("PUT", "/projects/" + kw[0] + "/categories", fDes, "{\"categories\":[" + q(cat[0]) + "]}");
            check(a.status == 200, "could not assign category to IDP: " + a.brief());
            Resp r = json("PUT", "/categories/" + cat[0] + "/availability", fMgr, "{\"available\":false}");
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            check(!get("/categories/available", fDes).body.contains(cat[0]), "category still offered to designers");
            check(get("/projects/" + kw[0], fCust).status == 200, "IDP using the category broke (data corruption)");
            String m = r.messages().toLowerCase();
            check(m.contains("warn") || m.contains("in use") || m.contains("assigned") || m.contains("existing"),
                    "category was made unavailable but no warning about IDPs using it was shown (message: " + r.messages() + ")");
            return null;
        });
        tc("TC6.6", "User report", () -> { na("reports have no per-role user breakdown (only a 'registration' count per period)"); return null; });
        tc("TC6.7", "Activity report with data", () -> {
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
        tc("TC6.8", "Report with no data", () -> {
            String[] range = in("TC6.8", "range").split("\\.\\.");
            Resp r = get("/reports/activity?period=daily&date=" + range[0], fMgr);
            check(r.status == 200, "expected HTTP 200, got " + r.brief());
            int[] z = reportTotals(fMgr, range[0]);
            check(z[0] + z[1] + z[2] + z[3] == 0, "expected zeros, got " + java.util.Arrays.toString(z));
            Resp t = get("/reports/table?period=monthly&date=" + range[0], fMgr);
            check(t.status == 200, "table report errored: " + t.brief());
            return "'" + r.messages() + "'";
        });

        summary();
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

    // ================================================================== minimal JSON reader (no libraries needed)
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
