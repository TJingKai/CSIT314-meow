from app.control.customer_controller import BrowseController, FavouriteController
from app.control.admin_controller import AccountController
from app.control.designer_controller import PortfolioController
from app.control.errors import AppError
from app.entity.category import Category
from tests.base import BaseTest


class CustomerTests(BaseTest):
    def setUp(self):
        super().setUp()
        self.cat2 = Category.create("Scandinavian Bedroom")
        self.a = PortfolioController.create(self.designer, {"title": "Sunny Kitchen", "category_id": self.cat_id,
                                                            "status": "current", "budget": 20000})
        self.b = PortfolioController.create(self.designer, {"title": "Calm Bedroom", "category_id": self.cat2,
                                                            "status": "past", "budget": 50000})

    def test_search_filters(self):
        s = BrowseController.search
        self.assertEqual(len(s(self.customer)), 2)
        self.assertEqual(len(s(self.customer, "kitchen")), 1)
        self.assertEqual(len(s(self.customer, category_id=self.cat2)), 1)
        self.assertEqual(len(s(self.customer, status="past")), 1)
        self.assertEqual(len(s(self.customer, max_budget=30000)), 1)

    def test_suspended_designer_hidden_from_search(self):
        AccountController.set_suspended(self.admin, self.designer["id"], True)
        self.assertEqual(len(BrowseController.search(self.customer)), 0)

    def test_view_returns_details_for_customers_only(self):
        self.assertEqual(BrowseController.view(self.customer, self.a["id"])["category_name"], "Modern Kitchen")
        with self.assertRaises(AppError):
            BrowseController.view(self.designer, self.a["id"])

    def test_view_missing_idp_404(self):
        with self.assertRaises(AppError) as cm:
            BrowseController.view(self.customer, 9999)
        self.assertEqual(cm.exception.status, 404)

    def test_favourite_add_list_remove(self):
        FavouriteController.add(self.customer, self.a["id"])
        self.assertEqual([f["title"] for f in FavouriteController.list(self.customer)], ["Sunny Kitchen"])
        self.assertEqual(len(FavouriteController.list(self.customer, "bedroom")), 0)
        FavouriteController.remove(self.customer, self.a["id"])
        self.assertEqual(FavouriteController.list(self.customer), [])

    def test_duplicate_favourite_rejected(self):
        FavouriteController.add(self.customer, self.a["id"])
        with self.assertRaises(AppError) as cm:
            FavouriteController.add(self.customer, self.a["id"])
        self.assertEqual(cm.exception.status, 409)

    def test_remove_missing_favourite_404(self):
        with self.assertRaises(AppError):
            FavouriteController.remove(self.customer, self.a["id"])
