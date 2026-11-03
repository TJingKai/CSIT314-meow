class AppError(Exception):
    """Business-rule failure carrying an HTTP-style status code."""

    def __init__(self, message, status=400):
        super().__init__(message)
        self.message = message
        self.status = status


def require_role(actor, role):
    if not actor or actor["role"] != role:
        raise AppError(f"Only {role} can do this", 403)


def require_fields(data, *names):
    missing = [n for n in names if not str(data.get(n, "")).strip()]
    if missing:
        raise AppError("Missing required field(s): " + ", ".join(missing), 400)
