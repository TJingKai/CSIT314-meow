import sqlite3

from app.control.errors import AppError, require_role, require_fields
from app.entity.category import Category
from app.entity.profile import PLATFORM
from app.entity.report import Report, PERIOD_DAYS


class CategoryController:
    @staticmethod
    def create(actor, data):
        require_role(actor, PLATFORM)
        require_fields(data, "name")
        try:
            return Category.get(Category.create(data["name"], data.get("description", "")))
        except sqlite3.IntegrityError:
            raise AppError("Category name already exists", 409)

    @staticmethod
    def view(actor, category_id):
        require_role(actor, PLATFORM)
        c = Category.get(category_id)
        if not c:
            raise AppError("Category not found", 404)
        return c

    @staticmethod
    def update(actor, category_id, data):
        CategoryController.view(actor, category_id)
        try:
            Category.update(category_id, data.get("name"), data.get("description"))
        except sqlite3.IntegrityError:
            raise AppError("Category name already exists", 409)
        return Category.get(category_id)

    @staticmethod
    def delete(actor, category_id):
        CategoryController.view(actor, category_id)
        if Category.in_use(category_id):
            raise AppError("Category is used by existing IDPs", 409)
        Category.delete(category_id)

    @staticmethod
    def search(actor, keyword=""):
        """Any logged-in user may list categories (customers/designers need them for filters and forms)."""
        if not actor:
            raise AppError("Not logged in", 401)
        return Category.search(keyword)


class ReportController:
    @staticmethod
    def generate(actor, period):
        require_role(actor, PLATFORM)
        if period not in PERIOD_DAYS:
            raise AppError("period must be daily, weekly or monthly", 400)
        return Report.generate(period)
