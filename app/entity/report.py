from datetime import datetime, timedelta, timezone

from app import db

PERIOD_DAYS = {"daily": 1, "weekly": 7, "monthly": 30}


class Report:
    @staticmethod
    def since_for(period):
        days = PERIOD_DAYS[period]
        return (datetime.now(timezone.utc) - timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")

    @staticmethod
    def generate(period):
        since = Report.since_for(period)
        p = (since,)
        return {
            "period": period,
            "since": since,
            "new_accounts": db.scalar("SELECT COUNT(*) FROM account WHERE created_at >= ?", p),
            "new_idps": db.scalar("SELECT COUNT(*) FROM idp WHERE created_at >= ?", p),
            "total_views": db.scalar("SELECT COUNT(*) FROM view_event WHERE viewed_at >= ?", p),
            "total_shortlists": db.scalar("SELECT COUNT(*) FROM favourite WHERE created_at >= ?", p),
            "top_categories": db.query(
                "SELECT c.name, COUNT(v.id) AS views FROM view_event v JOIN idp i ON i.id = v.idp_id "
                "JOIN category c ON c.id = i.category_id WHERE v.viewed_at >= ? "
                "GROUP BY c.id ORDER BY views DESC LIMIT 5", p),
            "top_idps": db.query(
                "SELECT i.id, i.title, COUNT(v.id) AS views FROM view_event v JOIN idp i ON i.id = v.idp_id "
                "WHERE v.viewed_at >= ? GROUP BY i.id ORDER BY views DESC LIMIT 5", p),
        }
