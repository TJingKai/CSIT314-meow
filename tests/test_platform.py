from app.control.customer_controller import BrowseController, FavouriteController
from app.control.designer_controller import PortfolioController
from app.control.errors import AppError
from app.control.platform_controller import CategoryController, ReportController
from tests.base import BaseTest


class CategoryTests(BaseTest):
    def test_crud(self):
        c = CategoryController.create(self.platform, {"name": "Japandi Study"})
        self.assertEqual(CategoryController.update(self.platform, c["id"], {"description": "calm"})["description"], "calm")
        self.assertEqual(len(CategoryController.search(self.platform, "japandi")), 1)
        CategoryController.delete(self.platform, c["id"])
        with self.assertRaises(AppError):
            CategoryController.view(self.platform, c["id"])

    def test_duplicate_name_rejected(self):
        with self.assertRaises(AppError) as cm:
            CategoryController.create(self.platform, {"name": "Modern Kitchen"})
        self.assertEqual(cm.exception.status, 409)

    def test_cannot_delete_category_in_use(self):
        PortfolioController.create(self.designer, {"title": "x", "category_id": self.cat_id, "status": "past"})
        with self.assertRaises(AppError) as cm:
            CategoryController.delete(self.platform, self.cat_id)
        self.assertEqual(cm.exception.status, 409)

    def test_only_platform_mgmt_can_create(self):
        with self.assertRaises(AppError):
            CategoryController.create(self.customer, {"name": "Sneaky"})

    def test_any_logged_in_user_can_list(self):
        self.assertEqual(len(CategoryController.search(self.customer)), 1)


class ReportTests(BaseTest):
    def test_report_counts_activity(self):
        idp = PortfolioController.create(self.designer, {"title": "x", "category_id": self.cat_id, "status": "current"})
        BrowseController.view(self.customer, idp["id"])
        BrowseController.view(self.customer, idp["id"])
        FavouriteController.add(self.customer, idp["id"])
        for period in ("daily", "weekly", "monthly"):
            r = ReportController.generate(self.platform, period)
            self.assertEqual((r["new_idps"], r["total_views"], r["total_shortlists"]), (1, 2, 1))
            self.assertEqual(r["top_categories"][0]["name"], "Modern Kitchen")

    def test_invalid_period_rejected(self):
        with self.assertRaises(AppError):
            ReportController.generate(self.platform, "yearly")

    def test_customer_cannot_view_reports(self):
        with self.assertRaises(AppError):
            ReportController.generate(self.customer, "daily")
