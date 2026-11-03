from datetime import datetime, timezone


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def like(keyword):
    return f"%{(keyword or '').strip()}%"
