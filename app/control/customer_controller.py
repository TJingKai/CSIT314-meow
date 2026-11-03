import sqlite3

from app.control.errors import AppError, require_role
from app.entity.idp import IDP
from app.entity.interest import ViewEvent, Favourite
from app.entity.profile import CUSTOMER


class BrowseController:
    @staticmethod
    def search(actor, keyword="", category_id=None, status=None, max_budget=None):
        require_role(actor, CUSTOMER)
        return IDP.search_public(keyword, category_id, status, max_budget)

    @staticmethod
    def view(actor, idp_id):
        """Returns IDP details and records one view event."""
        require_role(actor, CUSTOMER)
        idp = IDP.get(idp_id)
        if not idp:
            raise AppError("IDP not found", 404)
        ViewEvent.record(idp_id, actor["id"])
        return idp


class FavouriteController:
    @staticmethod
    def add(actor, idp_id):
        require_role(actor, CUSTOMER)
        if not IDP.get(idp_id):
            raise AppError("IDP not found", 404)
        try:
            Favourite.add(actor["id"], idp_id)
        except sqlite3.IntegrityError:
            raise AppError("Already in your favourites", 409)

    @staticmethod
    def remove(actor, idp_id):
        require_role(actor, CUSTOMER)
        if not Favourite.exists(actor["id"], idp_id):
            raise AppError("Not in your favourites", 404)
        Favourite.remove(actor["id"], idp_id)

    @staticmethod
    def list(actor, keyword=""):
        require_role(actor, CUSTOMER)
        return Favourite.list_for_customer(actor["id"], keyword)
