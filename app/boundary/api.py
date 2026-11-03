"""Boundary layer: Flask REST API. Parses requests, calls controllers, formats responses. No business logic."""
from functools import wraps

from flask import Flask, jsonify, request

from app import db
from app.control.admin_controller import AccountController, ProfileController
from app.control.auth_controller import AuthController
from app.control.customer_controller import BrowseController, FavouriteController
from app.control.designer_controller import CompanyController, PortfolioController, InterestController
from app.control.errors import AppError
from app.control.platform_controller import CategoryController, ReportController


def create_app():
    app = Flask(__name__)
    db.init_db()

    def protected(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            token = request.headers.get("Authorization", "").removeprefix("Bearer ").strip()
            try:
                return fn(AuthController.authenticate(token), *args, **kwargs)
            except AppError as e:
                return jsonify(error=e.message), e.status
        return wrapper

    def public(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            try:
                return fn(*args, **kwargs)
            except AppError as e:
                return jsonify(error=e.message), e.status
        return wrapper

    def body():
        return request.get_json(silent=True) or {}

    def arg(name, cast=str, default=None):
        v = request.args.get(name)
        return cast(v) if v not in (None, "") else default

    # ---- auth ----
    @app.post("/login")
    @public
    def login():
        d = body()
        token, actor = AuthController.login(d.get("username", ""), d.get("password", ""))
        return jsonify(token=token, user=actor)

    @app.post("/logout")
    @protected
    def logout(actor):
        AuthController.logout(request.headers["Authorization"].removeprefix("Bearer ").strip())
        return jsonify(message="Logged out")

    # ---- User Admin: accounts & profiles ----
    @app.get("/accounts")
    @protected
    def accounts_search(actor):
        return jsonify(AccountController.search(actor, arg("q", default=""), arg("profile_id", int)))

    @app.post("/accounts")
    @protected
    def accounts_create(actor):
        return jsonify(AccountController.create(actor, body())), 201

    @app.get("/accounts/<int:aid>")
    @protected
    def accounts_view(actor, aid):
        return jsonify(AccountController.view(actor, aid))

    @app.put("/accounts/<int:aid>")
    @protected
    def accounts_update(actor, aid):
        return jsonify(AccountController.update(actor, aid, body()))

    @app.post("/accounts/<int:aid>/suspend")
    @protected
    def accounts_suspend(actor, aid):
        return jsonify(AccountController.set_suspended(actor, aid, True))

    @app.post("/accounts/<int:aid>/unsuspend")
    @protected
    def accounts_unsuspend(actor, aid):
        return jsonify(AccountController.set_suspended(actor, aid, False))

    @app.get("/profiles")
    @protected
    def profiles_search(actor):
        return jsonify(ProfileController.search(actor, arg("q", default="")))

    @app.post("/profiles")
    @protected
    def profiles_create(actor):
        return jsonify(ProfileController.create(actor, body())), 201

    @app.get("/profiles/<int:pid>")
    @protected
    def profiles_view(actor, pid):
        return jsonify(ProfileController.view(actor, pid))

    @app.put("/profiles/<int:pid>")
    @protected
    def profiles_update(actor, pid):
        return jsonify(ProfileController.update(actor, pid, body()))

    @app.post("/profiles/<int:pid>/suspend")
    @protected
    def profiles_suspend(actor, pid):
        return jsonify(ProfileController.set_suspended(actor, pid, True))

    @app.post("/profiles/<int:pid>/unsuspend")
    @protected
    def profiles_unsuspend(actor, pid):
        return jsonify(ProfileController.set_suspended(actor, pid, False))

    # ---- Interior Designer: company, portfolio, interest ----
    @app.get("/my/company")
    @protected
    def company_view(actor):
        return jsonify(CompanyController.view(actor))

    @app.put("/my/company")
    @protected
    def company_save(actor):
        return jsonify(CompanyController.save(actor, body()))

    @app.get("/my/idps")
    @protected
    def my_idps(actor):
        return jsonify(PortfolioController.search(actor, arg("q", default=""), arg("status")))

    @app.post("/my/idps")
    @protected
    def my_idps_create(actor):
        return jsonify(PortfolioController.create(actor, body())), 201

    @app.get("/my/idps/stats")
    @protected
    def my_idps_stats(actor):
        return jsonify(InterestController.stats_all(actor, arg("since")))

    @app.get("/my/idps/<int:iid>")
    @protected
    def my_idps_view(actor, iid):
        return jsonify(PortfolioController.view(actor, iid))

    @app.put("/my/idps/<int:iid>")
    @protected
    def my_idps_update(actor, iid):
        return jsonify(PortfolioController.update(actor, iid, body()))

    @app.delete("/my/idps/<int:iid>")
    @protected
    def my_idps_delete(actor, iid):
        PortfolioController.delete(actor, iid)
        return "", 204

    @app.get("/my/idps/<int:iid>/stats")
    @protected
    def my_idp_stats(actor, iid):
        return jsonify(InterestController.stats_for(actor, iid, arg("since")))

    # ---- Customer: browse & favourites ----
    @app.get("/idps")
    @protected
    def idps_search(actor):
        return jsonify(BrowseController.search(actor, arg("q", default=""), arg("category_id", int),
                                               arg("status"), arg("max_budget", float)))

    @app.get("/idps/<int:iid>")
    @protected
    def idps_view(actor, iid):
        return jsonify(BrowseController.view(actor, iid))

    @app.get("/favourites")
    @protected
    def fav_list(actor):
        return jsonify(FavouriteController.list(actor, arg("q", default="")))

    @app.post("/favourites/<int:iid>")
    @protected
    def fav_add(actor, iid):
        FavouriteController.add(actor, iid)
        return jsonify(message="Added to favourites"), 201

    @app.delete("/favourites/<int:iid>")
    @protected
    def fav_remove(actor, iid):
        FavouriteController.remove(actor, iid)
        return "", 204

    # ---- Platform Management: categories & reports ----
    @app.get("/categories")
    @protected
    def cat_search(actor):
        return jsonify(CategoryController.search(actor, arg("q", default="")))

    @app.post("/categories")
    @protected
    def cat_create(actor):
        return jsonify(CategoryController.create(actor, body())), 201

    @app.get("/categories/<int:cid>")
    @protected
    def cat_view(actor, cid):
        return jsonify(CategoryController.view(actor, cid))

    @app.put("/categories/<int:cid>")
    @protected
    def cat_update(actor, cid):
        return jsonify(CategoryController.update(actor, cid, body()))

    @app.delete("/categories/<int:cid>")
    @protected
    def cat_delete(actor, cid):
        CategoryController.delete(actor, cid)
        return "", 204

    @app.get("/reports/<period>")
    @protected
    def report(actor, period):
        return jsonify(ReportController.generate(actor, period))

    return app
