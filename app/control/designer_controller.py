from app.control.errors import AppError, require_role, require_fields
from app.entity.category import Category
from app.entity.company import Company
from app.entity.idp import IDP
from app.entity.interest import ViewEvent, Favourite
from app.entity.profile import DESIGNER


class CompanyController:
    @staticmethod
    def view(actor):
        require_role(actor, DESIGNER)
        return Company.get_by_designer(actor["id"])

    @staticmethod
    def save(actor, data):
        require_role(actor, DESIGNER)
        require_fields(data, "name")
        return Company.save(actor["id"], data["name"], data.get("description", ""),
                            data.get("contact", ""), data.get("address", ""))


class PortfolioController:
    """Interior Designer manages their own IDPs."""

    @staticmethod
    def _own(actor, idp_id):
        require_role(actor, DESIGNER)
        idp = IDP.get(idp_id)
        if not idp:
            raise AppError("IDP not found", 404)
        if idp["designer_id"] != actor["id"]:
            raise AppError("You can only manage your own IDPs", 403)
        return idp

    @staticmethod
    def _validate(data, partial=False):
        if not partial:
            require_fields(data, "title", "category_id", "status")
        if data.get("status") and data["status"] not in ("current", "past"):
            raise AppError("status must be 'current' or 'past'", 400)
        if data.get("category_id") and not Category.get(data["category_id"]):
            raise AppError("Category not found", 404)
        if data.get("budget") is not None and float(data["budget"]) < 0:
            raise AppError("budget cannot be negative", 400)

    @staticmethod
    def create(actor, data):
        require_role(actor, DESIGNER)
        PortfolioController._validate(data)
        new_id = IDP.create(actor["id"], data["category_id"], data["title"], data.get("description", ""),
                            data["status"], data.get("budget", 0), data.get("image_url", ""))
        return IDP.get(new_id)

    @staticmethod
    def view(actor, idp_id):
        return PortfolioController._own(actor, idp_id)

    @staticmethod
    def update(actor, idp_id, data):
        PortfolioController._own(actor, idp_id)
        PortfolioController._validate(data, partial=True)
        IDP.update(idp_id, **data)
        return IDP.get(idp_id)

    @staticmethod
    def delete(actor, idp_id):
        PortfolioController._own(actor, idp_id)
        IDP.delete(idp_id)

    @staticmethod
    def search(actor, keyword="", status=None):
        require_role(actor, DESIGNER)
        return IDP.list_by_designer(actor["id"], keyword, status)


class InterestController:
    """Interior Designer tracks interest in their IDPs."""

    @staticmethod
    def stats_for(actor, idp_id, since=None):
        idp = PortfolioController._own(actor, idp_id)
        return {"idp_id": idp_id, "title": idp["title"],
                "views": ViewEvent.count(idp_id, since), "shortlists": Favourite.count(idp_id, since)}

    @staticmethod
    def stats_all(actor, since=None):
        require_role(actor, DESIGNER)
        return [InterestController.stats_for(actor, i["id"], since) for i in IDP.list_by_designer(actor["id"])]
