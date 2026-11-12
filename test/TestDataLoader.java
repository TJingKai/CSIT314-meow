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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads ./testdata (from TestDataGenerator) into a running MEOW app through its REST API, so
 * passwords are hashed and the app's own validation rules apply.
 *
 * Usage: java TestDataLoader.java [base url]   (default http://127.0.0.1:8000)
 * The app must be running on a fresh database with --meow.seed-demo=true.
 *
 * Timestamps in the CSVs are not back-dated (everything is stamped now), budget and dates go into
 * projectDetails, IDPs marked is_deleted are skipped and owner views are not loaded.
 */
public class TestDataLoader {

    static String BASE = "http://127.0.0.1:8000";
    static final String DATA = "testdata";
    static final String DEMO_PASSWORD = "MEOWDemo!2026";
    static final byte[] PDF = "%PDF-1.4 test proof document".getBytes(StandardCharsets.UTF_8);
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    static int errors = 0;
    static final List<String> errorLog = new ArrayList<>();

    // http helpers
    static class Sess { String cookie, csrf; }

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

        String messages() {
            StringBuilder sb = new StringBuilder();
            Object m = at("messages");
            if (m instanceof List)
                for (Object x : (List<?>) m) if (x instanceof Map) sb.append(((Map<?, ?>) x).get("text")).append(" | ");
            Object e = at("error", "message");
            if (e != null) sb.append(e);
            return sb.toString();
        }

        String brief() { return "HTTP " + status + " " + messages(); }
    }

    static String lastSetCookie;

    static Resp send(String method, String path, Sess s, String contentType, byte[] body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + "/api/v1" + path)).timeout(Duration.ofSeconds(60));
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

    static Resp get(String path, Sess s) throws Exception { return send("GET", path, s, null, null); }

    static Resp json(String method, String path, Sess s, String body) throws Exception {
        return send(method, path, s, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    static String q(String v) { return Json.quote(v); }

    static class Multipart {
        final String boundary = "----MeowLoader" + UUID.randomUUID().toString().replace("-", "");
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

    static void fail(String what, Resp r) {
        errors++;
        String line = what + " -> " + (r == null ? "no response" : r.brief());
        if (errorLog.size() < 15) errorLog.add(line);
    }

    // api actions
    /** Returns a session, or null when the credentials are not accepted. */
    static Sess tryLogin(String email, String pw) throws Exception {
        Resp r = json("POST", "/auth/session", null, "{\"email\":" + q(email) + ",\"password\":" + q(pw) + "}");
        if (r.status != 200 || lastSetCookie == null) return null;
        Sess s = new Sess();
        s.cookie = lastSetCookie.split(";", 2)[0].split("=", 2)[1];
        s.csrf = get("/auth/session", s).text("data", "csrfToken");
        return s;
    }

    static boolean alreadyExists(Resp r) {
        return r.status == 409 || (r.status == 400 && r.messages().toLowerCase().contains("already"));
    }

    static Sess ensureCustomer(String email, String pw) throws Exception {
        Sess s = tryLogin(email, pw);
        if (s != null) return s;
        Resp r = json("POST", "/registrations", null,
                "{\"email\":" + q(email) + ",\"password\":" + q(pw) + ",\"confirmPassword\":" + q(pw) + "}");
        if (r.status != 201 && !alreadyExists(r)) { fail("register customer " + email, r); return null; }
        return tryLogin(email, pw);
    }

    static Sess ensureDesigner(Sess admin, String email, String pw, String company, String uen) throws Exception {
        Sess s = tryLogin(email, pw);
        if (s != null) return s;
        Multipart m = new Multipart().field("email", email).field("password", pw).field("confirmPassword", pw)
                .field("role", "designer").field("company", company).field("uen", uen)
                .file("proof", "proof.pdf", "application/pdf", PDF);
        Resp r = send("POST", "/registrations", null, "multipart/form-data; boundary=" + m.boundary, m.build());
        if (r.status != 201) { fail("register designer " + email, r); return null; }
        Resp a = json("PUT", "/users/" + r.text("data", "id") + "/verification", admin, "{\"decision\":\"approve\"}");
        if (a.status != 200) { fail("approve designer " + email, a); return null; }
        return tryLogin(email, pw);
    }

    static boolean ensureAdmin(Sess admin, String email, String pw) throws Exception {
        Resp r = json("POST", "/users", admin, "{\"email\":" + q(email) + ",\"password\":" + q(pw)
                + ",\"confirmPassword\":" + q(pw) + ",\"role\":\"admin\"}");
        if (r.status == 201 || alreadyExists(r)) return true;
        fail("create admin " + email, r);
        return false;
    }

    // csv
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
        return rows.subList(1, rows.size());   // drop header
    }

    // main
    public static void main(String[] args) throws Exception {
        if (args.length > 0) BASE = args[0].replaceAll("/+$", "");
        String envUrl = System.getenv("MEOW_URL");
        if (args.length == 0 && envUrl != null && !envUrl.isBlank()) BASE = envUrl.replaceAll("/+$", "");

        List<List<String>> users, companies, categories, idps, images, saved, views;
        try {
            users = readCsv("users.csv");              // 0 id,1 role,2 name,3 email,4 plain pw
            companies = readCsv("id_company_profiles.csv"); // 1 designer_id,2 name,7 description
            categories = readCsv("categories.csv");    // 0 id,1 name,2 is_available
            idps = readCsv("idps.csv");                // 0 id,1 designer,2 title,3 desc,4 budget,5 category,6 status,7 start,8 end,9 deleted
            images = readCsv("idp_images.csv");        // 1 idp_id
            saved = readCsv("saved_idps.csv");         // 1 customer_id,2 idp_id
            views = readCsv("idp_views.csv");          // 1 idp_id,2 viewer_id,3 role,6 counted
        } catch (Exception e) {
            System.err.println("Cannot read generated test data: " + e.getMessage());
            System.exit(2);
            return;
        }
        if (!waitForApp()) {
            System.err.println("The MEOW application is not reachable at " + BASE + " (waited 90 s).");
            System.err.println("Start it first:  java -jar meow.jar --meow.seed-demo=true");
            System.exit(2);
        }
        // the health check answers before the demo accounts are seeded, so give seeding a moment
        // (at most 8 tries: the app locks an account after 10 failed logins)
        Sess admin = null, mgr = null;
        for (int i = 0; i < 8 && (admin == null || mgr == null); i++) {
            if (i > 0) Thread.sleep(3000);
            if (admin == null) admin = tryLogin("admin@meow.demo", DEMO_PASSWORD);
            if (mgr == null) mgr = tryLogin("manager@meow.demo", DEMO_PASSWORD);
        }
        if (admin == null || mgr == null) {
            System.err.println("Cannot log in as the demo admin/manager. Start the app with --meow.seed-demo=true on a FRESH database.");
            System.exit(2);
            return;
        }
        System.out.println("Loading ./" + DATA + " into " + BASE);

        // 1. categories (manager)
        Map<String, String> catByName = new HashMap<>();
        Resp existing = get("/categories?flow=edit", mgr);
        if (existing.at("data", "categories") instanceof List)
            for (Object o : (List<?>) existing.at("data", "categories"))
                catByName.put(String.valueOf(((Map<?, ?>) o).get("name")).toLowerCase(), String.valueOf(((Map<?, ?>) o).get("id")));
        Map<String, String> catApp = new HashMap<>();       // csv id -> app id
        Map<String, Boolean> catAvailable = new HashMap<>();
        int catNew = 0;
        for (List<String> c : categories) {
            String id = catByName.get(c.get(1).toLowerCase());
            if (id == null) {
                Resp r = json("POST", "/categories", mgr, "{\"name\":" + q(c.get(1)) + ",\"description\":" + q("Test data category") + "}");
                if (r.status == 201) { id = r.text("data", "id"); catNew++; }
                else fail("create category " + c.get(1), r);
            }
            if (id != null) catApp.put(c.get(0), id);
            catAvailable.put(c.get(0), Boolean.parseBoolean(c.get(2)));
        }
        System.out.println("categories : " + catApp.size() + " usable (" + catNew + " created)");

        // 2. users
        Map<String, String[]> userById = new HashMap<>();   // csv id -> {email, pw, name}
        Map<String, String> companyName = new HashMap<>(), companyDesc = new HashMap<>();
        for (List<String> c : companies) { companyName.put(c.get(1), c.get(2)); companyDesc.put(c.get(1), c.get(7)); }
        Map<String, Sess> sess = new HashMap<>();           // csv user id -> session
        int cust = 0, des = 0, adm = 0;
        for (List<String> u : users) {
            userById.put(u.get(0), new String[]{u.get(3), u.get(4), u.get(2)});
            switch (u.get(1)) {
                case "CUSTOMER" -> {
                    Sess s = ensureCustomer(u.get(3), u.get(4));
                    if (s != null) { sess.put(u.get(0), s); cust++; }
                }
                case "INTERIOR_DESIGNER" -> {
                    String name = companyName.getOrDefault(u.get(0), u.get(2) + " Studio");
                    Sess s = ensureDesigner(admin, u.get(3), u.get(4), name, "UEN-" + u.get(0));
                    if (s != null) {
                        sess.put(u.get(0), s);
                        des++;
                        int year = 2000 + (des % 24);
                        Resp r = json("PUT", "/me/company", s, "{\"name\":" + q(name) + ",\"description\":"
                                + q(companyDesc.getOrDefault(u.get(0), "Interior design firm")) + ",\"uen\":" + q("UEN-" + u.get(0))
                                + ",\"year\":" + year + "}");
                        if (r.status != 200) fail("company profile " + u.get(0), r);
                    }
                }
                case "ADMIN" -> { if (ensureAdmin(admin, u.get(3), u.get(4))) adm++; }
                default -> { }
            }
        }
        System.out.println("users      : " + cust + " customers, " + des + " designers (with company profile), " + adm + " admins");

        // 3. IDPs
        Map<String, List<String>> photoNames = new HashMap<>();
        for (List<String> im : images) photoNames.computeIfAbsent(im.get(1), k -> new ArrayList<>()).add(im.get(2));
        Path photoDir = Paths.get(DATA, "photos");
        Files.createDirectories(photoDir);
        Map<String, Map<String, String>> ownTitles = new HashMap<>();   // designer csv id -> title -> app id
        Map<String, String> idpApp = new HashMap<>();                  // csv idp id -> app idp id
        int created = 0, reused = 0, skippedDeleted = 0;
        for (List<String> p : idps) {
            if (Boolean.parseBoolean(p.get(9))) { skippedDeleted++; continue; }
            Sess d = sess.get(p.get(1));
            if (d == null) { fail("IDP " + p.get(0) + ": designer " + p.get(1) + " has no session", null); continue; }
            Map<String, String> titles = ownTitles.get(p.get(1));
            if (titles == null) {
                titles = new HashMap<>();
                Resp mine = get("/projects?mine=true", d);
                if (mine.at("data", "idps") instanceof List)
                    for (Object o : (List<?>) mine.at("data", "idps"))
                        titles.put(String.valueOf(((Map<?, ?>) o).get("title")), String.valueOf(((Map<?, ?>) o).get("id")));
                ownTitles.put(p.get(1), titles);
            }
            if (titles.containsKey(p.get(2))) { idpApp.put(p.get(0), titles.get(p.get(2))); reused++; continue; }

            String stage = p.get(6).equals("COMPLETED") ? "Past" : "Current";
            String details = "Budget: SGD " + p.get(4) + ". Started " + p.get(7)
                    + (p.get(8).isEmpty() ? "." : ", completed " + p.get(8) + ".");
            Resp r = json("POST", "/projects", d, "{\"title\":" + q(p.get(2)) + ",\"description\":" + q(p.get(3))
                    + ",\"projectDetails\":" + q(details) + ",\"status\":" + q(stage) + "}");
            if (r.status != 201) { fail("create IDP " + p.get(0), r); continue; }
            String pid = r.text("data", "id");
            idpApp.put(p.get(0), pid);
            created++;

            List<String> names = photoNames.getOrDefault(p.get(0), List.of(p.get(0).toLowerCase() + "_1.jpg"));
            Multipart m = new Multipart();
            for (int i = 0; i < names.size(); i++) {
                byte[] jpg = StockPhoto.variant(p.get(0), i);
                Files.write(photoDir.resolve(names.get(i)), jpg);
                m.file("photos", names.get(i), "image/jpeg", jpg);
            }
            m.field("coverIndex", "0");
            Resp up = send("POST", "/projects/" + pid + "/photos", d, "multipart/form-data; boundary=" + m.boundary, m.build());
            if (up.status != 200) fail("photos for " + p.get(0), up);

            if (Boolean.TRUE.equals(catAvailable.get(p.get(5))) && catApp.containsKey(p.get(5))) {
                Resp cr = json("PUT", "/projects/" + pid + "/categories", d, "{\"categories\":[" + q(catApp.get(p.get(5))) + "]}");
                if (cr.status != 200) fail("category for " + p.get(0), cr);
            }
            Resp pub = json("PUT", "/projects/" + pid + "/publication", d, "{\"action\":\"Publish\"}");
            if (pub.status != 200) fail("publish " + p.get(0), pub);
        }
        System.out.println("IDPs       : " + created + " created and published, " + reused + " already there, "
                + skippedDeleted + " flagged is_deleted skipped");

        // 4. unavailable categories
        // done after the IDPs, because the app only accepts available categories on an IDP
        int off = 0;
        for (List<String> c : categories) {
            if (Boolean.parseBoolean(c.get(2)) || !catApp.containsKey(c.get(0))) continue;
            Resp r = json("PUT", "/categories/" + catApp.get(c.get(0)) + "/availability", mgr, "{\"available\":false}");
            if (r.status == 200) off++; else fail("make category unavailable " + c.get(0), r);
        }
        System.out.println("categories : " + off + " set to unavailable");

        // 5. favourites, shortlists, views
        if (created == 0) {
            System.out.println("favourites : skipped (no new IDPs were created, so this looks like a re-run)");
            System.out.println("views      : skipped (same reason)");
        } else {
            int fav = 0, sh = 0, vw = 0;
            for (List<String> s : saved) {
                Sess c = sess.get(s.get(1));
                String pid = idpApp.get(s.get(2));
                if (c == null || pid == null) continue;
                Resp f = send("PUT", "/me/favourites/" + pid, c, null, null);
                if (f.status == 201 || f.status == 200) fav++; else { fail("favourite " + s.get(0), f); continue; }
                Resp sr = send("POST", "/projects/" + pid + "/shortlists", c, null, null);
                if (sr.status == 200) sh++; else fail("shortlist " + s.get(0), sr);
            }
            System.out.println("favourites : " + fav + " saved, " + sh + " shortlists recorded");

            for (List<String> v : views) {
                if (!v.get(6).equals("true") || !v.get(3).equals("CUSTOMER")) continue;   // owner views are never counted
                Sess c = sess.get(v.get(2));
                String pid = idpApp.get(v.get(1));
                if (c == null || pid == null) continue;
                Resp r = send("POST", "/projects/" + pid + "/views", c, null, null);
                if (r.status == 200) vw++; else fail("view " + v.get(0), r);
            }
            System.out.println("views      : " + vw + " recorded");
        }

        System.out.println("------------------------------------------------------------------");
        if (errors == 0) System.out.println("DONE: no errors. Now run: java TestRunner.java");
        else {
            System.out.println("DONE with " + errors + " problem(s). First ones:");
            for (String e : errorLog) System.out.println("  " + e);
            System.out.println("Fix the cause (a fresh DB with --meow.seed-demo=true solves most of them), then re-run.");
        }
        System.exit(errors == 0 ? 0 : 1);
    }

    static boolean waitForApp() throws Exception {
        for (int i = 0; i < 90; i++) {
            try { if (get("/health", null).status == 200) return true; } catch (Exception ignored) { }
            Thread.sleep(1000);
        }
        return false;
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

    /**
     * Test IDP photos made from the interior photos bundled in ../meow.jar (static/assets/photos).
     * Each photo is a different crop of one of them, sometimes mirrored, so cards do not repeat.
     */
    static class StockPhoto {
        static final int W = 1200, H = 800;
        static List<java.awt.image.BufferedImage> photos;

        static synchronized List<java.awt.image.BufferedImage> photos() throws Exception {
            if (photos != null) return photos;
            System.setProperty("java.awt.headless", "true");
            Path jar = Paths.get("..", "meow.jar");
            if (!Files.exists(jar)) throw new IllegalStateException("meow.jar not found next to the test folder");
            photos = new ArrayList<>();
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                List<String> names = new ArrayList<>();
                zip.stream().map(java.util.zip.ZipEntry::getName)
                        .filter(n -> n.matches("BOOT-INF/classes/static/assets/photos/[^/]+\\.jpg")).sorted().forEach(names::add);
                for (String n : names)
                    try (java.io.InputStream in = zip.getInputStream(zip.getEntry(n))) {
                        photos.add(javax.imageio.ImageIO.read(in));
                    }
            }
            if (photos.isEmpty()) throw new IllegalStateException("no photos in meow.jar static/assets/photos");
            return photos;
        }

        static byte[] variant(String id, int index) throws Exception {
            List<java.awt.image.BufferedImage> all = photos();
            java.util.Random rnd = new java.util.Random((id + "#" + index).hashCode());
            java.awt.image.BufferedImage src = all.get(Math.floorMod(rnd.nextInt(), all.size()));
            double zoom = 0.62 + rnd.nextDouble() * 0.38;
            int cw = (int) Math.min(src.getWidth() * zoom, src.getHeight() * zoom * 1.5);
            int ch = cw * 2 / 3;
            int x = rnd.nextInt(src.getWidth() - cw + 1), y = rnd.nextInt(src.getHeight() - ch + 1);
            boolean mirror = rnd.nextBoolean();

            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            if (mirror) g.drawImage(src, W, 0, 0, H, x, y, x + cw, y + ch, null);
            else g.drawImage(src, 0, 0, W, H, x, y, x + cw, y + ch, null);
            g.dispose();

            javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.82f);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
            return bytes.toByteArray();
        }
    }
}
