import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Test data generator for the Interior Design platform.
 *
 * Generates CSV files (>= 100 records per data type) into ./testdata/
 *   users.csv, id_company_profiles.csv, categories.csv, idps.csv, idp_images.csv,
 *   saved_idps.csv, idp_views.csv, idp_stats.csv
 * plus the data that drives the test cases themselves:
 *   test_case_data.csv      (concrete input values + expected result for TC1.1 - TC6.6)
 *   expected_reports.csv    (expected figures for TC6.5 - TC6.6, computed from the data)
 *   use_case_data.csv       (inputs + exact expected messages for every use case in the use-case
 *                            descriptions PDF: #220 - #251, #256, #263, #266, #273; long format:
 *                            uc_id,name,key,value)
 *
 * Compile:  javac TestDataGenerator.java
 * Run:      java TestDataGenerator            (fixed seed -> reproducible data)
 *           java TestDataGenerator 12345      (optional custom seed)
 */
public class TestDataGenerator {

    // configuration
    static final int N = 100;               // records per core data type
    static final int VIEW_EVENTS = 300;     // customer view events
    static final int OWNER_VIEWS = 10;      // designers viewing their own IDP (TC5.6)
    static final int MAX_IMAGES = 5;        // allowed images per IDP (TC3.8)
    static final int MAX_LOGO_KB = 2048;    // logo size limit (TC2.4)
    static final int IDS_WITHOUT_IDP = 10;  // designers with empty portfolio (TC3.9)
    static final int IDPS_WITHOUT_ACTIVITY = 10; // IDPs with 0 views / shortlists (TC5.5)
    static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    static final String OUT = "testdata";

    static Random R;

    // simple data holders
    static class User {
        String id, role, name, email, plainPw, status;
        LocalDateTime created;
    }

    static class Idp {
        String id, designerId, title, description, status, categoryId;
        int budget, index;
        LocalDate start, end;
        boolean deleted;
    }

    static class Saved {
        String customerId, idpId;
        LocalDateTime at;
    }

    static class View {
        String idpId, viewerId, viewerRole, session;
        LocalDateTime at;
        boolean counted;
    }

    // vocab
    static final String[] FIRST = {"Aiden", "Mei", "Raj", "Sofia", "Daniel", "Priya", "Wei", "Hannah", "Omar", "Chloe",
            "Farah", "Lucas", "Nadia", "Ethan", "Yuki", "Isabel", "Kenji", "Amara", "Jonas", "Lina"};
    static final String[] LAST = {"Tan", "Lim", "Nair", "Garcia", "Wong", "Patel", "Chen", "Ahmad", "Lee", "Kumar",
            "Ng", "Santos", "Ong", "Rahman", "Goh", "Silva", "Teo", "Ibrahim", "Koh", "Singh"};
    static final String[] ROOMS = {"Kitchen", "Living Room", "Bedroom", "Bathroom", "Office", "Dining Room",
            "Balcony", "Kids Room", "Entryway", "Study"};
    static final String[] STYLES = {"Modern", "Minimalist", "Scandinavian", "Industrial", "Rustic", "Contemporary",
            "Japandi", "Bohemian", "Classic", "Coastal"};
    static final String[] ADJ = {"Bright", "Cozy", "Elegant", "Compact", "Spacious", "Warm", "Sleek", "Airy"};
    static final String[] COMPANY_SUFFIX = {"Interiors", "Design Studio", "Living", "Spaces", "Atelier", "Concepts"};
    static final String[] PHRASES = {
            "Open layout with natural light and warm timber accents.",
            "Space-saving built-in storage and a neutral colour palette.",
            "Custom carpentry with durable, easy-to-clean surfaces.",
            "Layered lighting that suits both work and relaxation.",
            "Statement feature wall paired with simple furniture.",
            "Sustainable materials sourced from local suppliers."};

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20261108L;
        R = new Random(seed);
        Files.createDirectories(Paths.get(OUT));

        // users
        List<User> customers = makeUsers("CUS", "CUSTOMER", N);
        List<User> designers = makeUsers("IDS", "INTERIOR_DESIGNER", N);
        List<User> admins = makeUsers("ADM", "ADMIN", 3);
        List<User> allUsers = new ArrayList<>();
        allUsers.addAll(customers);
        allUsers.addAll(designers);
        allUsers.addAll(admins);
        writeUsers(allUsers);

        // company profiles (one per designer)
        writeCompanies(designers);

        // categories
        List<String[]> categories = makeCategories();
        write("categories.csv", new String[]{"category_id", "name", "is_available"}, categories);

        // IDPs + images
        List<Idp> idps = makeIdps(designers, categories);
        writeIdps(idps);
        writeImages(idps);

        // saved list, views, stats
        List<Saved> saved = makeSaved(customers, idps);
        writeSaved(saved);
        List<View> views = makeViews(customers, idps);
        writeViews(views);
        writeStats(idps, saved, views);

        // test-case driven data + expected reports
        writeTestCaseData(customers, designers, idps, saved, categories);
        writeExpectedReports(allUsers, saved, views);
        writeUseCaseData();

        System.out.println("Test data written to ./" + OUT + " (seed=" + seed + ")");
        System.out.println("users=" + allUsers.size() + ", companies=" + designers.size()
                + ", categories=" + categories.size() + ", idps=" + idps.size()
                + ", saved=" + saved.size() + ", views=" + views.size());
    }

    // Generators
    static List<User> makeUsers(String prefix, String role, int count) {
        List<User> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User u = new User();
            u.id = String.format("%s-%03d", prefix, i);
            u.role = role;
            String f = pick(FIRST), l = pick(LAST);
            u.name = f + " " + l;
            u.email = (f + "." + l).toLowerCase() + prefix.toLowerCase() + i + "@example.com"; // unique
            u.plainPw = "Passw0rd!" + String.format("%03d", i);   // strong password (>= 8 chars, mixed)
            u.status = "ACTIVE";
            u.created = randomPast(400, 800);
            list.add(u);
        }
        return list;
    }

    static List<String[]> makeCategories() {
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (String room : ROOMS) {
            for (String style : STYLES) {
                String name = room + " - " + style;   // plain "Kitchen" is deliberately NOT seeded: TC6.1 creates it, TC6.2 duplicates it
                boolean available = (n % 20 != 0);                           // 5 unavailable categories
                rows.add(new String[]{String.format("CAT-%03d", n), name, String.valueOf(available)});
                n++;
            }
        }
        return rows;
    }

    static List<Idp> makeIdps(List<User> designers, List<String[]> categories) {
        List<Idp> list = new ArrayList<>();
        int designersWithProjects = designers.size() - IDS_WITHOUT_IDP;
        Set<Integer> deletedIdx = new HashSet<>();
        deletedIdx.add(10);
        deletedIdx.add(40);
        deletedIdx.add(70);
        for (int i = 0; i < N; i++) {
            Idp p = new Idp();
            p.index = i;
            p.id = String.format("IDP-%03d", i + 1);
            p.designerId = designers.get(R.nextInt(designersWithProjects)).id;
            p.categoryId = categories.get(R.nextInt(categories.size()))[0];
            String style = pick(STYLES), room = pick(ROOMS);
            p.title = pick(ADJ) + " " + style + " " + room + " #" + (i + 1);
            String desc = pick(PHRASES) + " " + pick(PHRASES);
            if (i % 8 == 0) desc = "A quiet retreat. " + desc;       // keyword "quiet" -> TC4.1
            p.description = desc;                                    // never contains "zzzzzz" -> TC4.2
            p.budget = 5000 + R.nextInt(195001);
            p.start = TODAY.minusDays(60 + R.nextInt(940));
            boolean completed = (i % 5 < 3);                         // 60% completed, 40% current
            p.status = completed ? "COMPLETED" : "CURRENT";
            p.end = completed ? minDate(p.start.plusDays(14 + R.nextInt(166)), TODAY.minusDays(1)) : null;
            p.deleted = deletedIdx.contains(i);
            list.add(p);
        }
        return list;
    }

    static List<Saved> makeSaved(List<User> customers, List<Idp> idps) {
        List<Saved> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int eligible = N - IDPS_WITHOUT_ACTIVITY;
        while (list.size() < N) {
            User c = customers.get(R.nextInt(customers.size()));
            Idp p = idps.get(R.nextInt(eligible));
            if (!seen.add(c.id + "|" + p.id)) continue;             // a customer saves an IDP only once
            Saved s = new Saved();
            s.customerId = c.id;
            s.idpId = p.id;
            s.at = randomPast(1, 365);
            list.add(s);
        }
        return list;
    }

    static List<View> makeViews(List<User> customers, List<Idp> idps) {
        List<View> list = new ArrayList<>();
        int eligible = N - IDPS_WITHOUT_ACTIVITY;
        while (list.size() < VIEW_EVENTS) {
            View v = new View();
            if (!list.isEmpty() && R.nextInt(100) < 20) {            // repeat view in same session (TC5.2) - counted again (UC #241)
                View prev = list.get(R.nextInt(list.size()));
                if (!prev.viewerRole.equals("CUSTOMER")) continue;
                v.idpId = prev.idpId;
                v.viewerId = prev.viewerId;
                v.session = prev.session;
                v.at = prev.at.plusMinutes(1 + R.nextInt(20));
            } else {
                Idp p = idps.get(R.nextInt(eligible));
                if (p.deleted) continue;
                v.idpId = p.id;
                v.viewerId = customers.get(R.nextInt(customers.size())).id;
                v.session = "SES-" + (100000 + R.nextInt(900000));
                v.at = randomPast(1, 365);
            }
            v.viewerRole = "CUSTOMER";
            v.counted = true;   // use case #241: every view of a published IDP by an authenticated viewer is recorded
            list.add(v);
        }
        // designers opening their own IDP -> never counted (TC5.6)
        int added = 0;
        while (added < OWNER_VIEWS) {
            Idp p = idps.get(R.nextInt(eligible));
            if (p.deleted) continue;
            View v = new View();
            v.idpId = p.id;
            v.viewerId = p.designerId;
            v.viewerRole = "INTERIOR_DESIGNER";
            v.session = "SES-" + (100000 + R.nextInt(900000));
            v.at = randomPast(1, 365);
            v.counted = false;
            list.add(v);
            added++;
        }
        return list;
    }

    // Writers
    static void writeUsers(List<User> users) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (User u : users) {
            rows.add(new String[]{u.id, u.role, u.name, u.email, u.plainPw, sha256(u.plainPw), u.status, u.created.toString()});
        }
        write("users.csv", new String[]{"user_id", "role", "full_name", "email", "password_plain_for_testing",
                "password_sha256", "status", "created_at"}, rows);
    }

    static void writeCompanies(List<User> designers) {
        List<String[]> rows = new ArrayList<>();
        String[] logoExt = {"png", "jpg", "jpeg"};
        int n = 1;
        for (User d : designers) {
            String last = d.name.split(" ")[1];
            String company = last + " " + pick(COMPANY_SUFFIX);
            String ext = (n == 1) ? "png" : pick(logoExt);
            int sizeKb = (n == 1) ? 500 : 20 + R.nextInt(1900);      // company 1 -> exactly 500 KB PNG (TC2.3)
            rows.add(new String[]{
                    String.format("COM-%03d", n), d.id, company,
                    "Blk " + (1 + R.nextInt(999)) + " " + pick(LAST) + " Street " + (1 + R.nextInt(20))
                            + ", Singapore " + (100000 + R.nextInt(899999)),
                    "+65 " + pick(new String[]{"6", "8", "9"}) + (1000000 + R.nextInt(8999999)),
                    "contact@" + company.toLowerCase().replace(" ", "") + ".example.com",
                    "https://www." + company.toLowerCase().replace(" ", "") + ".example.com",
                    "Full-service interior design firm specialising in residential projects.",
                    "logo_" + n + "." + ext, String.valueOf(sizeKb),
                    "INTERNAL: negotiated rate tier " + (1 + R.nextInt(3)) + " - do not publish",  // must never be public (TC2.6)
                    d.created.plusDays(1).toString()});
            n++;
        }
        write("id_company_profiles.csv", new String[]{"company_id", "designer_id", "company_name", "address", "phone",
                "public_email", "website", "description", "logo_file", "logo_size_kb", "private_notes", "created_at"}, rows);
    }

    static void writeIdps(List<Idp> idps) {
        List<String[]> rows = new ArrayList<>();
        for (Idp p : idps) {
            rows.add(new String[]{p.id, p.designerId, p.title, p.description, String.valueOf(p.budget), p.categoryId,
                    p.status, p.start.toString(), p.end == null ? "" : p.end.toString(), String.valueOf(p.deleted)});
        }
        write("idps.csv", new String[]{"idp_id", "designer_id", "title", "description", "budget_sgd", "category_id",
                "status", "start_date", "end_date", "is_deleted"}, rows);
    }

    static void writeImages(List<Idp> idps) {
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (Idp p : idps) {
            int count = p.status.equals("COMPLETED") ? 3 + R.nextInt(MAX_IMAGES - 2) : 1 + R.nextInt(3); // 3..5 or 1..3
            for (int k = 1; k <= count; k++) {
                rows.add(new String[]{String.format("IMG-%04d", n++), p.id, p.id.toLowerCase() + "_" + k + ".jpg",
                        String.valueOf(100 + R.nextInt(1800)), String.valueOf(k)});
            }
        }
        write("idp_images.csv", new String[]{"image_id", "idp_id", "file_name", "size_kb", "position"}, rows);
    }

    static void writeSaved(List<Saved> saved) {
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (Saved s : saved) {
            rows.add(new String[]{String.format("SAV-%03d", n++), s.customerId, s.idpId, s.at.toString()});
        }
        write("saved_idps.csv", new String[]{"saved_id", "customer_id", "idp_id", "saved_at"}, rows);
    }

    static void writeViews(List<View> views) {
        List<String[]> rows = new ArrayList<>();
        int n = 1;
        for (View v : views) {
            rows.add(new String[]{String.format("VW-%04d", n++), v.idpId, v.viewerId, v.viewerRole, v.session,
                    v.at.toString(), String.valueOf(v.counted)});
        }
        write("idp_views.csv", new String[]{"view_id", "idp_id", "viewer_id", "viewer_role", "session_id",
                "viewed_at", "counted"}, rows);
    }

    static void writeStats(List<Idp> idps, List<Saved> saved, List<View> views) {
        List<String[]> rows = new ArrayList<>();
        for (Idp p : idps) {
            int vc = 0, sc = 0;
            for (View v : views) if (v.idpId.equals(p.id) && v.counted) vc++;
            for (Saved s : saved) if (s.idpId.equals(p.id)) sc++;
            rows.add(new String[]{p.id, String.valueOf(vc), String.valueOf(sc)});
        }
        write("idp_stats.csv", new String[]{"idp_id", "view_count", "shortlist_count"}, rows);
    }

    // Concrete input data for every test case in the six tables
    static void writeTestCaseData(List<User> customers, List<User> designers, List<Idp> idps,
                                  List<Saved> saved, List<String[]> categories) {
        List<String[]> t = new ArrayList<>();
        User cust = customers.get(0);
        User des = designers.get(0);
        User desNoIdp = designers.get(designers.size() - 1);
        Idp completed = firstWith(idps, "COMPLETED");
        Idp current = firstWith(idps, "CURRENT");
        Idp noActivity = idps.get(N - 1);
        Idp quietIdp = idps.get(0);   // description contains "quiet"
        Saved sv = saved.get(0);

        // TC1 - user accounts
        t.add(tc("TC1.1", "Register valid", "email=new.customer@example.com; password=Str0ng!Pass1; name=Test User", "Account created, redirect to homepage"));
        t.add(tc("TC1.2", "Register duplicate email", "email=" + cust.email + "; password=Str0ng!Pass1", "Rejected: 'email already in use'"));
        t.add(tc("TC1.3", "Register weak password", "email=weak.pw@example.com; password=ab1", "Rejected with password rule message"));
        t.add(tc("TC1.4", "Register missing field", "email=; password=Str0ng!Pass1", "Inline validation error, no account created"));
        t.add(tc("TC1.5", "Login valid", "email=" + cust.email + "; password=" + cust.plainPw, "Redirect to homepage"));
        t.add(tc("TC1.6", "Login wrong password", "email=" + cust.email + "; password=WrongPass!999", "Generic 'invalid credentials' error"));
        t.add(tc("TC1.7", "Logout", "user=" + cust.email + "; click Logout then browser Back", "Session ended, protected pages not accessible"));

        // TC2 - company information
        t.add(tc("TC2.1", "Create company profile", "designer=" + des.email + "; all mandatory fields from id_company_profiles.csv (COM-001)", "Profile saved, shown on ID dashboard"));
        t.add(tc("TC2.2", "Missing mandatory field", "designer=" + des.email + "; company_name=", "Validation error, nothing saved"));
        t.add(tc("TC2.3", "Edit company info", "address=1 Orchard Road, Singapore 238824", "New address shown on public page"));
        t.add(tc("TC2.4", "Customer views company", "customer=" + cust.email + "; open COM-001 page", "Public fields shown; private_notes / password NOT shown"));

        // TC3 - IDP portfolio
        t.add(tc("TC3.1", "Add current IDP", "designer=" + des.email + "; status=CURRENT; end_date=blank", "Appears under 'Current'"));
        t.add(tc("TC3.2", "Add past IDP with images", "status=COMPLETED; images=3 (e.g. " + completed.id + " has images in idp_images.csv)", "Appears under 'Past' with all images"));
        t.add(tc("TC3.3", "Missing title", "title=", "Rejected"));
        t.add(tc("TC3.4", "Mark current as completed", "idp=" + current.id + "; status=COMPLETED; end_date=2026-11-01", "Moves to 'Past'"));
        t.add(tc("TC3.5", "Edit IDP details", "idp=" + completed.id + "; description=Updated text; budget=88000", "Updated in portfolio and customer view"));
        t.add(tc("TC3.6", "Empty portfolio", "designer=" + desNoIdp.email + " (no IDPs)", "Friendly 'no projects yet' message"));
        
        // TC4 - customer search
        t.add(tc("TC4.1", "Keyword search with matches", "keyword=quiet (e.g. " + quietIdp.id + ")", "Only matching IDPs shown"));
        t.add(tc("TC4.2", "Search no results", "keyword=zzzzzz", "Empty list and the message 'No projects match your search' (use case #238, 3a)"));
        t.add(tc("TC4.3", "Clear filters", "remove all filters", "Full list returns"));
        t.add(tc("TC4.4", "View IDP details", "open " + completed.id, "Full details, images and ID info displayed"));
        t.add(tc("TC4.5", "Save IDP", "customer=" + cust.email + "; save " + current.id, "In saved list, button shows 'Saved'"));
        t.add(tc("TC4.6", "Persistence", "log out and back in as " + sv.customerId, "Saved IDPs still present"));

        // TC5 - tracking
        t.add(tc("TC5.1", "View increments", "customer=" + cust.email + "; open " + current.id + " (new session)", "View count +1"));
        t.add(tc("TC5.2", "Shortlist increments", "customer saves " + current.id, "Shortlist count +1"));
        t.add(tc("TC5.3", "No activity", "idp=" + noActivity.id + " (0 views, 0 shortlists in idp_stats.csv)", "Shows 0 and 0"));
        
        // TC6 - platform management
        t.add(tc("TC6.1", "Create category", "name=Kitchen (not in seed data, so creation succeeds)", "Saved; appears in IDP forms and filters"));
        t.add(tc("TC6.2", "Duplicate category", "name=Kitchen (run after TC6.1)", "Rejected as already exists"));
        t.add(tc("TC6.3", "Empty category name", "name=", "Validation error"));
        t.add(tc("TC6.4", "Edit category", "CAT-002 rename to 'Kitchen Renovated'", "Name updates everywhere it is used"));
        t.add(tc("TC6.5", "Activity report with data", "range=2026-01-01..2026-06-30", "Figures match expected_reports.csv"));
        t.add(tc("TC6.6", "Report with no data", "range=2000-01-01..2000-01-31", "Zeros or 'no data', no error"));

        write("test_case_data.csv", new String[]{"tc_id", "scenario", "input_data", "expected_result"}, t);
    }

    static void writeExpectedReports(List<User> users, List<Saved> saved, List<View> views) {
        List<String[]> rows = new ArrayList<>();
        String[][] ranges = {{"2026-01-01", "2026-06-30", "TC6.5"}, {"2000-01-01", "2000-01-31", "TC6.6"}};
        for (String[] r : ranges) {
            LocalDateTime from = LocalDate.parse(r[0]).atStartOfDay();
            LocalDateTime to = LocalDate.parse(r[1]).atTime(LocalTime.MAX);
            int vc = 0, sc = 0;
            for (View v : views) if (v.counted && !v.at.isBefore(from) && !v.at.isAfter(to)) vc++;
            for (Saved s : saved) if (!s.at.isBefore(from) && !s.at.isAfter(to)) sc++;
            rows.add(new String[]{r[2], "counted_views", r[0] + ".." + r[1], String.valueOf(vc)});
            rows.add(new String[]{r[2], "shortlists", r[0] + ".." + r[1], String.valueOf(sc)});
        }
        write("expected_reports.csv", new String[]{"tc_id", "metric", "range", "expected_value"}, rows);
    }

    // Helpers
    static String[] tc(String id, String scenario, String input, String expected) {
        return new String[]{id, scenario, input, expected};
    }

    static Idp firstWith(List<Idp> idps, String status) {
        for (Idp p : idps) if (p.status.equals(status) && !p.deleted && p.index < N - IDPS_WITHOUT_ACTIVITY) return p;
        return idps.get(0);
    }

    static <T> T pick(T[] a) {
        return a[R.nextInt(a.length)];
    }

    static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static LocalDateTime randomPast(int minDays, int maxDays) {
        return TODAY.minusDays(minDays + R.nextInt(Math.max(1, maxDays - minDays + 1)))
                .atTime(R.nextInt(24), R.nextInt(60), R.nextInt(60));
    }

    static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Use-case driven data (use-case descriptions PDF) - long format: uc_id,name,key,value
    // Message texts are copied verbatim from the PDF; TestRunner asserts on them.
    static void uc(List<String[]> rows, String id, String name, String... kv) {
        if (kv.length == 0) rows.add(new String[]{id, name, "-", "-"});
        for (int i = 0; i + 1 < kv.length; i += 2) rows.add(new String[]{id, name, kv[i], kv[i + 1]});
    }

    static void writeUseCaseData() {
        List<String[]> u = new ArrayList<>();
        String pw = "Str0ng!Pass1";
        uc(u, "UC#220", "Fill Customer Registration Form", "valid_password", pw, "mismatch_password", "Different!Pass2",
                "bad_email", "not-an-email", "msg_invalid_email", "Invalid email format", "msg_mismatch", "Passwords do not match");
        uc(u, "UC#221", "Create Customer Account", "password", pw,
                "msg_success", "Account created successfully", "msg_dup", "Email is already registered");
        uc(u, "UC#222", "Register Interior Designer Account", "password", pw, "proof_file", "business_proof.pdf",
                "msg_success", "Application submitted successfully", "msg_dup", "Email is already registered");
        uc(u, "UC#223", "Set Account Pending Verification", "msg_success", "Application submitted successfully");
        uc(u, "UC#224", "Log In", "msg_success", "Login successful",
                "msg_suspended", "This account does not have active access.", "msg_wrong", "Email or password is incorrect.");
        uc(u, "UC#225", "Log Out", "msg_success", "Logged out successfully");
        uc(u, "UC#226", "Role-Based Screens");
        uc(u, "UC#227", "Search Accounts", "no_match_keyword", "zzzzzz-no-such-account", "msg_no_match", "No accounts found");
        uc(u, "UC#228", "Create Account for Any Role", "password", pw, "roles", "customer,designer,admin,manager",
                "msg_success", "Account created successfully", "msg_mismatch", "Passwords do not match",
                "msg_dup", "Email is already in use");
        uc(u, "UC#229", "Suspend or Reactivate Account", "msg_success", "Account status updated successfully",
                "msg_own", "You cannot change your own status.");
        uc(u, "UC#230", "Delete Account", "msg_success", "Account deleted successfully",
                "msg_own", "You cannot delete your own account.");
        uc(u, "UC#231", "Edit Company Details", "year", "2016", "msg_success", "Company details saved successfully",
                "msg_dup_uen", "UEN is already registered");
        uc(u, "UC#232", "Public Company Profile", "unknown_id", "no-such-designer", "msg_not_found", "Designer profile not found");
        uc(u, "UC#256", "Approve or Reject Designer", "msg_approved", "Designer approved successfully",
                "msg_rejected", "Designer registration rejected", "msg_no_proof", "Business proof is required before approval.",
                "no_proof_designer", "sam@meow.demo");
        uc(u, "UC#233", "Create IDP", "msg_success", "IDP created successfully");
        uc(u, "UC#234", "Upload Photos and Choose Cover", "photos", "3", "cover", "1", "bad_cover", "9",
                "msg_success", "Photos uploaded successfully", "msg_cover", "Choose a valid cover image.",
                "msg_type", "Use PNG, JPEG or WebP images; business proof also accepts PDF.");
        uc(u, "UC#235", "Edit IDP", "msg_success", "IDP updated successfully");
        uc(u, "UC#236", "View Own IDP List");
        uc(u, "UC#237", "Search Published IDPs");
        uc(u, "UC#238", "View Search Results", "no_match_keyword", "zzzzzz", "msg_no_match", "No projects match your search");
        uc(u, "UC#239", "View IDP Details and Gallery", "unknown_id", "no-such-idp", "msg_not_found", "Resource not found.");
        uc(u, "UC#240", "Company Link from IDP", "unknown_id", "no-such-idp", "msg_not_found", "Resource not found.");
        uc(u, "UC#241", "Record IDP View");
        uc(u, "UC#242", "Save Favourite without Duplicates", "msg_success", "Saved to favourites", "msg_dup", "Already in your favourites");
        uc(u, "UC#243", "Record IDP Shortlist");
        uc(u, "UC#244", "Portfolio Totals");
        uc(u, "UC#245", "Stats per IDP");
        uc(u, "UC#263", "Publish or Unpublish IDP", "msg_publish", "IDP published successfully", "msg_unpublish", "IDP unpublished");
        uc(u, "UC#266", "Assign IDP Categories", "invalid_category", "no-such-category",
                "msg_success", "Categories saved successfully", "msg_none", "Select at least one category",
                "msg_invalid", "Choose available categories only.");
        uc(u, "UC#273", "View Favourites List");
        uc(u, "UC#246", "Create Category", "name", "UC Category", "description", "Created by the use-case test",
                "msg_success", "Category created successfully", "msg_empty", "Category name is required",
                "msg_dup", "Category already exists");
        uc(u, "UC#247", "Edit Category", "msg_success", "Category updated successfully",
                "msg_empty", "Category name is required", "msg_dup", "Category already exists");
        uc(u, "UC#248", "Make Category Available", "msg_off", "Category is unavailable for IDP creation",
                "msg_on", "Category is now available for IDP creation");
        uc(u, "UC#249", "Select Report Period", "periods", "daily,weekly,monthly", "msg_future", "Date cannot be in the future");
        uc(u, "UC#250", "Generate Activity Totals", "empty_date", "2000-01-05", "msg_none", "No activity in this period");
        uc(u, "UC#251", "View Daily Report Table", "empty_date", "2000-01-05", "msg_none", "No activity in this period");
        write("use_case_data.csv", new String[]{"uc_id", "name", "key", "value"}, u);
    }

    static String esc(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static void write(String file, String[] header, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header)).append("\n");
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(esc(r[i]));
            }
            sb.append("\n");
        }
        Path p = Paths.get(OUT, file);
        try {
            Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + p, e);
        }
        System.out.println("  wrote " + p + " (" + rows.size() + " rows)");
    }
}