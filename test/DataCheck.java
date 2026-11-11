import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Consistency check for the demo database.
 *
 *   java DataCheck.java csv   checks the files in ./testdata on their own (run before loading)
 *   java DataCheck.java db    compares the loaded H2 database with ./testdata (run after loading)
 *
 * The db mode reads ../instance/meow while the application is running (AUTO_SERVER mode), using
 * the H2 driver packed inside ../meow.jar. Exit code 0 = no problems, 1 = problems found, 2 = error.
 */
public class DataCheck {

    static final String DATA = "testdata";
    static final String SEED_ADMIN = "admin@meow.demo";
    static final String SEED_MANAGER = "manager@meow.demo";

    static final List<String> problems = new ArrayList<>();
    static int checks = 0;

    static void expect(boolean ok, String what) {
        checks++;
        if (!ok) problems.add(what);
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "db";
        try {
            if (mode.equals("csv")) checkCsv();
            else if (mode.equals("db")) checkDb(args.length > 1 ? args[1] : "..");
            else {
                System.err.println("Usage: java DataCheck.java csv|db [app folder]");
                System.exit(2);
            }
        } catch (Exception e) {
            System.err.println("Data check could not run: " + e);
            System.exit(2);
        }
        System.out.println("------------------------------------------------------------------");
        if (problems.isEmpty()) {
            System.out.println("DATA CHECK (" + mode + "): " + checks + " checks, no discrepancies");
            System.exit(0);
        }
        System.out.println("DATA CHECK (" + mode + "): " + problems.size() + (problems.size() == 1 ? " discrepancy" : " discrepancies")
                + " in " + checks + " checks");
        for (int i = 0; i < Math.min(problems.size(), 30); i++) System.out.println("  " + problems.get(i));
        if (problems.size() > 30) System.out.println("  ... and " + (problems.size() - 30) + " more");
        System.exit(1);
    }

    // csv mode
    static void checkCsv() throws Exception {
        List<Map<String, String>> users = csv("users.csv"), companies = csv("id_company_profiles.csv"),
                categories = csv("categories.csv"), idps = csv("idps.csv"), images = csv("idp_images.csv"),
                saved = csv("saved_idps.csv"), views = csv("idp_views.csv"), stats = csv("idp_stats.csv");

        Map<String, Map<String, String>> userById = unique(users, "user_id", "users.csv");
        unique(categories, "category_id", "categories.csv");
        Map<String, Map<String, String>> idpById = unique(idps, "idp_id", "idps.csv");
        unique(companies, "company_id", "id_company_profiles.csv");
        unique(images, "image_id", "idp_images.csv");
        unique(saved, "saved_id", "saved_idps.csv");
        unique(views, "view_id", "idp_views.csv");

        Set<String> emails = new HashSet<>();
        for (Map<String, String> u : users) {
            String e = u.get("email").toLowerCase();
            expect(emails.add(e), "users.csv: duplicate email " + e);
            expect(!e.equals(SEED_ADMIN) && !e.equals(SEED_MANAGER), "users.csv: clashes with a demo account " + e);
            expect(u.get("password_plain_for_testing").length() >= 8, "users.csv: password too short for " + u.get("user_id"));
        }
        Set<String> catNames = new HashSet<>();
        for (Map<String, String> c : categories)
            expect(catNames.add(c.get("name").toLowerCase()), "categories.csv: duplicate name " + c.get("name"));

        Set<String> designersWithCompany = new HashSet<>();
        for (Map<String, String> c : companies) {
            Map<String, String> owner = userById.get(c.get("designer_id"));
            expect(owner != null && owner.get("role").equals("INTERIOR_DESIGNER"),
                    "id_company_profiles.csv: " + c.get("company_id") + " is not owned by a designer");
            expect(designersWithCompany.add(c.get("designer_id")), "id_company_profiles.csv: two companies for " + c.get("designer_id"));
        }

        Set<String> catIds = new HashSet<>();
        for (Map<String, String> c : categories) catIds.add(c.get("category_id"));
        Set<String> titles = new HashSet<>();
        for (Map<String, String> p : idps) {
            Map<String, String> owner = userById.get(p.get("designer_id"));
            expect(owner != null && owner.get("role").equals("INTERIOR_DESIGNER"), "idps.csv: " + p.get("idp_id") + " has no designer owner");
            expect(catIds.contains(p.get("category_id")), "idps.csv: " + p.get("idp_id") + " uses unknown category " + p.get("category_id"));
            expect(titles.add(p.get("designer_id") + "|" + p.get("title")), "idps.csv: duplicate title for one designer " + p.get("title"));
            if (!p.get("end_date").isEmpty())
                expect(p.get("end_date").compareTo(p.get("start_date")) >= 0, "idps.csv: " + p.get("idp_id") + " ends before it starts");
        }

        Map<String, Integer> perIdpImages = new HashMap<>();
        for (Map<String, String> im : images) {
            expect(idpById.containsKey(im.get("idp_id")), "idp_images.csv: " + im.get("image_id") + " points to unknown IDP");
            perIdpImages.merge(im.get("idp_id"), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : perIdpImages.entrySet())
            expect(e.getValue() <= 20, "idp_images.csv: " + e.getKey() + " has more photos than the app allows");

        Map<String, Integer> expViews = new HashMap<>(), expShort = new HashMap<>();
        Set<String> pairs = new HashSet<>();
        for (Map<String, String> s : saved) {
            Map<String, String> c = userById.get(s.get("customer_id"));
            expect(c != null && c.get("role").equals("CUSTOMER"), "saved_idps.csv: " + s.get("saved_id") + " is not saved by a customer");
            expect(idpById.containsKey(s.get("idp_id")), "saved_idps.csv: " + s.get("saved_id") + " points to unknown IDP");
            expect(pairs.add(s.get("customer_id") + "|" + s.get("idp_id")), "saved_idps.csv: duplicate favourite " + s.get("saved_id"));
            expShort.merge(s.get("idp_id"), 1, Integer::sum);
        }
        for (Map<String, String> v : views) {
            Map<String, String> viewer = userById.get(v.get("viewer_id"));
            Map<String, String> idp = idpById.get(v.get("idp_id"));
            expect(viewer != null, "idp_views.csv: " + v.get("view_id") + " has unknown viewer");
            expect(idp != null, "idp_views.csv: " + v.get("view_id") + " points to unknown IDP");
            if (viewer == null || idp == null) continue;
            boolean own = idp.get("designer_id").equals(v.get("viewer_id"));
            expect(!own || v.get("counted").equals("false"), "idp_views.csv: owner view counted " + v.get("view_id"));
            expect(viewer.get("role").equals(v.get("viewer_role")), "idp_views.csv: viewer_role mismatch " + v.get("view_id"));
            if (v.get("counted").equals("true")) expViews.merge(v.get("idp_id"), 1, Integer::sum);
        }
        for (Map<String, String> s : stats) {
            String id = s.get("idp_id");
            expect(Integer.parseInt(s.get("view_count")) == expViews.getOrDefault(id, 0),
                    "idp_stats.csv: view_count for " + id + " does not match idp_views.csv");
            expect(Integer.parseInt(s.get("shortlist_count")) == expShort.getOrDefault(id, 0),
                    "idp_stats.csv: shortlist_count for " + id + " does not match saved_idps.csv");
        }
        System.out.println("Checked ./" + DATA + ": " + users.size() + " users, " + categories.size() + " categories, "
                + idps.size() + " IDPs, " + saved.size() + " favourites, " + views.size() + " views");
    }

    // db mode
    static void checkDb(String appDir) throws Exception {
        Path jar = Paths.get(appDir, "meow.jar");
        Path db = Paths.get(appDir, "instance", "meow").toAbsolutePath();
        if (!Files.exists(jar)) throw new IllegalStateException("meow.jar not found in " + Paths.get(appDir).toAbsolutePath());
        if (!Files.exists(Paths.get(db + ".mv.db"))) throw new IllegalStateException("no database at " + db);

        try (Connection con = connect(jar, "jdbc:h2:file:" + db + ";DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE;IFEXISTS=TRUE")) {
            Statement st = con.createStatement();
            integrity(st);
            compareWithCsv(st);
        }
    }

    static void integrity(Statement st) throws Exception {
        String[][] orphanQueries = {
            {"companies without owner", "SELECT COUNT(*) FROM companies c LEFT JOIN users u ON u.id=c.owner_id WHERE u.id IS NULL"},
            {"companies owned by non-designers", "SELECT COUNT(*) FROM companies c JOIN users u ON u.id=c.owner_id WHERE u.role<>'designer'"},
            {"projects owned by non-designers", "SELECT COUNT(*) FROM projects p JOIN users u ON u.id=p.owner_id WHERE u.role<>'designer'"},
            {"project categories pointing nowhere", "SELECT COUNT(*) FROM project_categories pc LEFT JOIN categories c ON c.id=pc.category_id LEFT JOIN projects p ON p.id=pc.project_id WHERE c.id IS NULL OR p.id IS NULL"},
            {"photos without project", "SELECT COUNT(*) FROM project_photos ph LEFT JOIN projects p ON p.id=ph.project_id WHERE p.id IS NULL"},
            {"photos with missing upload", "SELECT COUNT(*) FROM project_photos ph LEFT JOIN uploads up ON up.id=ph.upload_id WHERE ph.upload_id IS NOT NULL AND up.id IS NULL"},
            {"published projects without photos", "SELECT COUNT(*) FROM projects p WHERE p.published=1 AND NOT EXISTS (SELECT 1 FROM project_photos ph WHERE ph.project_id=p.id)"},
            {"projects with photos but not exactly one cover", "SELECT COUNT(*) FROM (SELECT project_id FROM project_photos GROUP BY project_id HAVING SUM(is_cover)<>1) x"},
            {"duplicate photo positions", "SELECT COUNT(*) FROM (SELECT project_id, position FROM project_photos GROUP BY project_id, position HAVING COUNT(*)>1) x"},
            {"published projects without publish date", "SELECT COUNT(*) FROM projects WHERE published=1 AND published_at IS NULL"},
            {"favourites by non-customers", "SELECT COUNT(*) FROM favourites f JOIN users u ON u.id=f.user_id WHERE u.role<>'customer'"},
            {"favourites on unpublished projects", "SELECT COUNT(*) FROM favourites f JOIN projects p ON p.id=f.project_id WHERE p.published=0"},
            {"view/shortlist events without project", "SELECT COUNT(*) FROM events WHERE type IN ('view','shortlist') AND project_id IS NULL"},
            {"views recorded by the project owner", "SELECT COUNT(*) FROM events e JOIN projects p ON p.id=e.project_id WHERE e.type='view' AND e.user_id=p.owner_id"},
            {"shortlists without a matching favourite", "SELECT COUNT(*) FROM events e WHERE e.type='shortlist' AND NOT EXISTS (SELECT 1 FROM favourites f WHERE f.user_id=e.user_id AND f.project_id=e.project_id)"},
            {"duplicate shortlists", "SELECT COUNT(*) FROM (SELECT user_id, project_id FROM events WHERE type='shortlist' GROUP BY user_id, project_id HAVING COUNT(*)>1) x"},
            {"active designers never verified", "SELECT COUNT(*) FROM users WHERE role='designer' AND status='active' AND verified_at IS NULL"},
            {"users with empty password hash", "SELECT COUNT(*) FROM users WHERE password_hash=''"},
            {"duplicate company UENs", "SELECT COUNT(*) FROM (SELECT LOWER(uen) FROM companies GROUP BY LOWER(uen) HAVING COUNT(*)>1) x"},
        };
        for (String[] q : orphanQueries) {
            long n = count(st, q[1]);
            expect(n == 0, "database: " + n + " " + q[0]);
        }
        expect(count(st, "SELECT COUNT(*) FROM users WHERE email='" + SEED_ADMIN + "' AND role='admin' AND status='active'") == 1,
                "database: built-in admin " + SEED_ADMIN + " missing or not active");
        expect(count(st, "SELECT COUNT(*) FROM users WHERE email='" + SEED_MANAGER + "' AND role='manager' AND status='active'") == 1,
                "database: demo manager " + SEED_MANAGER + " missing or not active");
    }

    static void compareWithCsv(Statement st) throws Exception {
        List<Map<String, String>> users = csv("users.csv"), companies = csv("id_company_profiles.csv"),
                categories = csv("categories.csv"), idps = csv("idps.csv"), images = csv("idp_images.csv"),
                saved = csv("saved_idps.csv"), views = csv("idp_views.csv");

        Map<String, String> emailOf = new HashMap<>();
        for (Map<String, String> u : users) emailOf.put(u.get("user_id"), u.get("email").toLowerCase());

        // users
        Map<String, String[]> dbUsers = new HashMap<>();
        try (ResultSet rs = st.executeQuery("SELECT LOWER(email), role, status, id FROM users")) {
            while (rs.next()) dbUsers.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
        }
        Map<String, String> roleMap = Map.of("CUSTOMER", "customer", "INTERIOR_DESIGNER", "designer", "ADMIN", "admin");
        for (Map<String, String> u : users) {
            String[] row = dbUsers.get(u.get("email").toLowerCase());
            expect(row != null, "users: " + u.get("user_id") + " " + u.get("email") + " not in database");
            if (row == null) continue;
            expect(row[0].equals(roleMap.get(u.get("role"))), "users: wrong role for " + u.get("email") + " (" + row[0] + ")");
            expect(row[1].equalsIgnoreCase(u.get("status")), "users: wrong status for " + u.get("email") + " (" + row[1] + ")");
        }

        // companies
        Map<String, String> dbCompany = new HashMap<>();
        try (ResultSet rs = st.executeQuery("SELECT LOWER(u.email), c.name FROM companies c JOIN users u ON u.id=c.owner_id")) {
            while (rs.next()) dbCompany.put(rs.getString(1), rs.getString(2));
        }
        for (Map<String, String> c : companies) {
            String email = emailOf.get(c.get("designer_id"));
            expect(c.get("company_name").equals(dbCompany.get(email)), "companies: " + c.get("company_id") + " missing or renamed for " + email);
        }

        // categories
        Map<String, Integer> dbCat = new HashMap<>();
        Map<String, String> catNameById = new HashMap<>();
        try (ResultSet rs = st.executeQuery("SELECT LOWER(name), active FROM categories")) {
            while (rs.next()) dbCat.put(rs.getString(1), rs.getInt(2));
        }
        for (Map<String, String> c : categories) {
            catNameById.put(c.get("category_id"), c.get("name").toLowerCase());
            Integer active = dbCat.get(c.get("name").toLowerCase());
            expect(active != null, "categories: " + c.get("category_id") + " " + c.get("name") + " not in database");
            if (active != null)
                expect((active == 1) == Boolean.parseBoolean(c.get("is_available")), "categories: availability differs for " + c.get("name"));
        }

        // projects, photos, categories
        Map<String, String> dbProject = new HashMap<>();   // email|title -> project id
        Map<String, Integer> dbPublished = new HashMap<>();
        try (ResultSet rs = st.executeQuery("SELECT LOWER(u.email), p.title, p.id, p.published FROM projects p JOIN users u ON u.id=p.owner_id")) {
            while (rs.next()) {
                String key = rs.getString(1) + "|" + rs.getString(2);
                expect(!dbProject.containsKey(key), "projects: duplicate title in database " + key);
                dbProject.put(key, rs.getString(3));
                dbPublished.put(rs.getString(3), rs.getInt(4));
            }
        }
        Map<String, Integer> dbPhotos = groupCount(st, "SELECT project_id, COUNT(*) FROM project_photos GROUP BY project_id");
        Map<String, Set<String>> dbProjectCats = new HashMap<>();
        try (ResultSet rs = st.executeQuery("SELECT pc.project_id, LOWER(c.name) FROM project_categories pc JOIN categories c ON c.id=pc.category_id")) {
            while (rs.next()) dbProjectCats.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2));
        }
        Map<String, Integer> csvPhotos = new HashMap<>();
        for (Map<String, String> im : images) csvPhotos.merge(im.get("idp_id"), 1, Integer::sum);
        Map<String, Boolean> catAvailable = new HashMap<>();
        for (Map<String, String> c : categories) catAvailable.put(c.get("category_id"), Boolean.parseBoolean(c.get("is_available")));

        Map<String, String> projectOf = new HashMap<>();    // csv idp id -> db project id
        for (Map<String, String> p : idps) {
            String key = emailOf.get(p.get("designer_id")) + "|" + p.get("title");
            String pid = dbProject.get(key);
            if (Boolean.parseBoolean(p.get("is_deleted"))) {
                expect(pid == null, "projects: " + p.get("idp_id") + " is flagged deleted but was loaded");
                continue;
            }
            expect(pid != null, "projects: " + p.get("idp_id") + " " + p.get("title") + " not in database");
            if (pid == null) continue;
            projectOf.put(p.get("idp_id"), pid);
            expect(dbPublished.get(pid) == 1, "projects: " + p.get("idp_id") + " is not published");
            int want = Math.max(1, csvPhotos.getOrDefault(p.get("idp_id"), 0));
            expect(dbPhotos.getOrDefault(pid, 0) == want,
                    "photos: " + p.get("idp_id") + " has " + dbPhotos.getOrDefault(pid, 0) + " photos, expected " + want);
            Set<String> cats = dbProjectCats.getOrDefault(pid, Set.of());
            if (catAvailable.getOrDefault(p.get("category_id"), false))
                expect(cats.equals(Set.of(catNameById.get(p.get("category_id")))), "categories: " + p.get("idp_id") + " assigned " + cats);
            else expect(cats.isEmpty(), "categories: " + p.get("idp_id") + " has an unavailable category assigned");
        }

        // favourites and shortlists
        Set<String> dbFav = new HashSet<>();
        try (ResultSet rs = st.executeQuery("SELECT LOWER(u.email), f.project_id FROM favourites f JOIN users u ON u.id=f.user_id")) {
            while (rs.next()) dbFav.add(rs.getString(1) + "|" + rs.getString(2));
        }
        Set<String> csvFav = new HashSet<>();
        Map<String, Integer> expShort = new HashMap<>();
        for (Map<String, String> s : saved) {
            String pid = projectOf.get(s.get("idp_id"));
            if (pid == null) continue;
            csvFav.add(emailOf.get(s.get("customer_id")) + "|" + pid);
            expShort.merge(pid, 1, Integer::sum);
        }
        Set<String> missing = new HashSet<>(csvFav);
        missing.removeAll(dbFav);
        expect(missing.isEmpty(), "favourites: " + missing.size() + " from saved_idps.csv not in database");
        Set<String> testEmails = new HashSet<>(emailOf.values());
        long extra = dbFav.stream().filter(f -> testEmails.contains(f.split("\\|")[0]) && !csvFav.contains(f)).count();
        expect(extra == 0, "favourites: " + extra + " in database that are not in saved_idps.csv");

        // views and shortlists per project
        Map<String, Integer> expViews = new HashMap<>();
        for (Map<String, String> v : views) {
            if (!v.get("counted").equals("true") || !v.get("viewer_role").equals("CUSTOMER")) continue;
            String pid = projectOf.get(v.get("idp_id"));
            if (pid != null) expViews.merge(pid, 1, Integer::sum);
        }
        Map<String, Integer> dbViews = groupCount(st, "SELECT project_id, COUNT(*) FROM events WHERE type='view' GROUP BY project_id");
        Map<String, Integer> dbShort = groupCount(st, "SELECT project_id, COUNT(*) FROM events WHERE type='shortlist' GROUP BY project_id");
        Map<String, String> csvIdOf = new TreeMap<>();
        for (Map.Entry<String, String> e : projectOf.entrySet()) csvIdOf.put(e.getValue(), e.getKey());
        for (String pid : projectOf.values()) {
            expect(dbViews.getOrDefault(pid, 0).equals(expViews.getOrDefault(pid, 0)),
                    "views: " + csvIdOf.get(pid) + " has " + dbViews.getOrDefault(pid, 0) + ", expected " + expViews.getOrDefault(pid, 0));
            expect(dbShort.getOrDefault(pid, 0).equals(expShort.getOrDefault(pid, 0)),
                    "shortlists: " + csvIdOf.get(pid) + " has " + dbShort.getOrDefault(pid, 0) + ", expected " + expShort.getOrDefault(pid, 0));
        }

        System.out.println("Database: " + dbUsers.size() + " users, " + dbCat.size() + " categories, " + dbProject.size()
                + " projects, " + dbFav.size() + " favourites, " + sum(dbViews) + " views, " + sum(dbShort) + " shortlists");
    }

    // helpers
    static Connection connect(Path jar, String url) throws Exception {
        Path h2 = null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().matches("BOOT-INF/lib/h2-.*\\.jar")) {
                    h2 = Files.createTempFile("meow-h2-", ".jar");
                    h2.toFile().deleteOnExit();
                    try (InputStream in = zip.getInputStream(e)) {
                        Files.copy(in, h2, StandardCopyOption.REPLACE_EXISTING);
                    }
                    break;
                }
            }
        }
        if (h2 == null) throw new IllegalStateException("H2 driver not found inside " + jar);
        URLClassLoader loader = new URLClassLoader(new URL[]{h2.toUri().toURL()}, DataCheck.class.getClassLoader());
        Driver driver = (Driver) Class.forName("org.h2.Driver", true, loader).getDeclaredConstructor().newInstance();
        Properties p = new Properties();
        p.setProperty("user", "sa");
        String pw = System.getenv("MEOW_DATABASE_PASSWORD");
        p.setProperty("password", pw == null ? "" : pw);
        return driver.connect(url, p);
    }

    static long count(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static Map<String, Integer> groupCount(Statement st, String sql) throws Exception {
        Map<String, Integer> m = new HashMap<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
        }
        return m;
    }

    static int sum(Map<String, Integer> m) {
        int s = 0;
        for (int v : m.values()) s += v;
        return s;
    }

    static Map<String, Map<String, String>> unique(List<Map<String, String>> rows, String key, String file) {
        Map<String, Map<String, String>> m = new HashMap<>();
        for (Map<String, String> r : rows) expect(m.put(r.get(key), r) == null, file + ": duplicate " + key + " " + r.get(key));
        return m;
    }

    static List<Map<String, String>> csv(String file) throws Exception {
        Path p = Paths.get(DATA, file);
        if (!Files.exists(p)) throw new IllegalStateException("missing " + p + " - run TestDataGenerator first");
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
        List<String> header = rows.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() == 1 && r.get(0).isEmpty()) continue;
            if (r.size() != header.size()) {
                problems.add(file + ": row has " + r.size() + " columns, header has " + header.size());
                continue;
            }
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < header.size(); i++) m.put(header.get(i), r.get(i));
            out.add(m);
        }
        return out;
    }
}
