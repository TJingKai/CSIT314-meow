"""Timestamp-based designer analytics and Singapore-calendar activity reports."""

from datetime import date, datetime, time, timedelta, timezone

from flask import Blueprint, g, jsonify, request

from .database import get_db, now
from .errors import ApiError, body, text
from .projects import project_row
from .security import require_user

api = Blueprint("analytics", __name__)


def parse_date(value):
    try:
        return date.fromisoformat(value)
    except (TypeError, ValueError):
        raise ApiError("Use an ISO date such as 2026-11-06.") from None


def utc_boundary(day):
    # Fixed UTC+8 avoids an external timezone database dependency on Windows.
    local_zone = timezone(timedelta(hours=8))
    return datetime.combine(day, time.min, local_zone).astimezone(timezone.utc).isoformat()


@api.get("/me/analytics")
@require_user("designer")
def analytics():
    clauses, args = ["p.owner_id=?"], [g.user["id"]]
    event_conditions, event_args = [], []
    start, end = request.args.get("from"), request.args.get("to")
    if start:
        event_conditions.append("e.created_at>=?")
        event_args.append(utc_boundary(parse_date(start)))
    if end:
        event_conditions.append("e.created_at<?")
        event_args.append(utc_boundary(parse_date(end) + timedelta(days=1)))
    if start and end and start > end:
        raise ApiError("Start date must be on or before end date.")
    extra = " AND " + " AND ".join(event_conditions) if event_conditions else ""
    rows = (
        get_db()
        .execute(
            "SELECT p.id,p.title,p.published,p.archived,"
            "SUM(CASE WHEN e.type='view' THEN 1 ELSE 0 END) AS views,"
            "SUM(CASE WHEN e.type='shortlist' THEN 1 ELSE 0 END) AS shortlists "
            "FROM projects p LEFT JOIN events e ON e.project_id=p.id"
            + extra
            + " WHERE "
            + " AND ".join(clauses)
            + " GROUP BY p.id ORDER BY p.title",
            (*event_args, *args),
        )
        .fetchall()
    )
    projects = [dict(row) for row in rows]
    return jsonify(
        data={
            "projects": projects,
            "totalViews": sum(p["views"] for p in projects),
            "totalShortlists": sum(p["shortlists"] for p in projects),
        }
    )


@api.get("/reports/activity")
@require_user("manager")
def report():
    selected = parse_date(request.args.get("date", ""))
    period = request.args.get("period", "daily")
    if period == "daily":
        start, end = selected, selected + timedelta(days=1)
    elif period == "weekly":
        start = selected - timedelta(days=selected.weekday())
        end = start + timedelta(days=7)
    elif period == "monthly":
        start = selected.replace(day=1)
        end = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    else:
        raise ApiError("period must be daily, weekly or monthly.")
    args, db = (utc_boundary(start), utc_boundary(end)), get_db()
    totals = {
        row["type"]: row["total"]
        for row in db.execute(
            "SELECT type,COUNT(*) AS total FROM events WHERE created_at>=? AND created_at<? GROUP BY type",
            args,
        )
    }
    rankings = {}
    for kind in ("view", "shortlist"):
        rankings[kind] = [
            dict(row)
            for row in db.execute(
                "SELECT p.id,p.title,COUNT(*) AS total FROM events e JOIN projects p ON p.id=e.project_id "
                "WHERE e.created_at>=? AND e.created_at<? AND e.type=? GROUP BY p.id "
                "ORDER BY total DESC,p.id LIMIT 5",
                (*args, kind),
            )
        ]
    return jsonify(
        data={
            "period": period,
            "from": start.isoformat(),
            "to": (end - timedelta(days=1)).isoformat(),
            "timezone": "Asia/Singapore",
            "totals": {
                key: totals.get(key, 0) for key in ("view", "shortlist", "registration", "project")
            },
            "topViewed": rankings["view"],
            "topShortlisted": rankings["shortlist"],
        }
    )


@api.get("/moderation/projects")
@require_user("manager")
def moderation():
    rows = (
        get_db()
        .execute(
            "SELECT p.id,p.title,p.published,p.archived,p.takedown_reason,c.name AS company "
            "FROM projects p LEFT JOIN companies c ON c.owner_id=p.owner_id "
            "ORDER BY p.created_at DESC"
        )
        .fetchall()
    )
    return jsonify(data=[dict(row) for row in rows])


@api.put("/projects/<identifier>/moderation")
@require_user("manager")
def takedown(identifier):
    project_row(identifier)
    reason = text(body(), "reason", maximum=1000)
    with get_db() as db:
        db.execute(
            "UPDATE projects SET takedown_reason=?,published=0,updated_at=? WHERE id=?",
            (reason, now(), identifier),
        )
    return jsonify(data={"id": identifier, "reason": reason, "published": False})
