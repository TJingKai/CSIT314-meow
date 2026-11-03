from app.control.customer_controller import BrowseController, FavouriteController
from app.control.designer_controller import CompanyController, PortfolioController, InterestController
from app.control.errors import AppError
from tests.base import BaseTest


class CompanyTests(BaseTest):
    def test_save_creates_then_updates_single_record(self):
        CompanyController.save(self.designer, {"name": "Tan Studio"})
        c = CompanyController.save(self.designer, {"name": "Tan Studio Pte", "contact": "123"})
        self.assertEqual(c["name"], "Tan Studio Pte")
        self.assertEqual(CompanyController.view(self.designer)["contact"], "123")

    def test_company_name_required(self):
        with self.assertRaises(AppError):
            CompanyController.save(self.designer, {"name": " "})

    def test_customer_cannot_edit_company(self):
        with self.assertRaises(AppError):
            CompanyController.save(self.customer, {"name": "x"})


class PortfolioTests(BaseTest):
    def new_idp(self, **over):
        data = {"title": "Sunny Kitchen", "category_id": self.cat_id, "status": "current", "budget": 20000}
        data.update(over)
        return PortfolioController.create(self.designer, data)

    def test_create_and_view(self):
        idp = self.new_idp()
        self.assertEqual(PortfolioController.view(self.designer, idp["id"])["title"], "Sunny Kitchen")

    def test_invalid_status_rejected(self):
        with self.assertRaises(AppError):
            self.new_idp(status="maybe")

    def test_unknown_category_rejected(self):
        with self.assertRaises(AppError) as cm:
            self.new_idp(category_id=9999)
        self.assertEqual(cm.exception.status, 404)

    def test_update_marks_project_past(self):
        idp = self.new_idp()
        self.assertEqual(PortfolioController.update(self.designer, idp["id"], {"status": "past"})["status"], "past")

    def test_cannot_modify_other_designers_idp(self):
        idp = self.new_idp()
        with self.assertRaises(AppError) as cm:
            PortfolioController.update(self.designer2, idp["id"], {"title": "hijack"})
        self.assertEqual(cm.exception.status, 403)

    def test_delete_removes_idp(self):
        idp = self.new_idp()
        PortfolioController.delete(self.designer, idp["id"])
        with self.assertRaises(AppError):
            PortfolioController.view(self.designer, idp["id"])

    def test_search_own_portfolio_by_keyword_and_status(self):
        self.new_idp(title="Sunny Kitchen")
        self.new_idp(title="Dark Study", status="past")
        self.assertEqual(len(PortfolioController.search(self.designer, "kitchen")), 1)
        self.assertEqual(len(PortfolioController.search(self.designer, "", "past")), 1)
        self.assertEqual(len(PortfolioController.search(self.designer2)), 0)


class InterestTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.idp = PortfolioController.create(self.designer, {"title": "A", "category_id": self.cat_id,
                                                              "status": "current"})

    def test_views_and_shortlists_counted(self):
        BrowseController.view(self.customer, self.idp["id"])
        BrowseController.view(self.customer, self.idp["id"])
        FavouriteController.add(self.customer, self.idp["id"])
        stats = InterestController.stats_for(self.designer, self.idp["id"])
        self.assertEqual((stats["views"], stats["shortlists"]), (2, 1))

    def test_stats_all_lists_each_idp(self):
        self.assertEqual(len(InterestController.stats_all(self.designer)), 1)

    def test_other_designer_cannot_see_stats(self):
        with self.assertRaises(AppError):
            InterestController.stats_for(self.designer2, self.idp["id"])

    def test_since_filter_excludes_old_events(self):
        BrowseController.view(self.customer, self.idp["id"])
        stats = InterestController.stats_for(self.designer, self.idp["id"], since="2999-01-01 00:00:00")
        self.assertEqual(stats["views"], 0)
