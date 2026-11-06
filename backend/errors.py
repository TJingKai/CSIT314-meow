"""Consistent API errors and input validation."""

import math
import re

from flask import jsonify, request


class ApiError(Exception):
    def __init__(self, message, status=400, code="validation_error"):
        super().__init__(message)
        self.message, self.status, self.code = message, status, code


def error_response(error):
    return jsonify(error={"code": error.code, "message": error.message}), error.status


def body():
    value = request.get_json(silent=True)
    if not isinstance(value, dict):
        raise ApiError("Send a JSON object with Content-Type: application/json.")
    return value


def text(data, key, required=True, maximum=500):
    value = data.get(key, "")
    if not isinstance(value, str):
        raise ApiError(f"{key} must be text.")
    value = value.strip()
    if (required and not value) or len(value) > maximum:
        raise ApiError(
            f"{key} is required and must be at most {maximum} characters."
            if required
            else f"{key} must be at most {maximum} characters."
        )
    return value


def number(data, key, minimum=0, maximum=1_000_000_000):
    value = data.get(key)
    if isinstance(value, bool):
        raise ApiError(f"{key} must be a number.")
    try:
        result = float(value)
    except (TypeError, ValueError):
        raise ApiError(f"{key} must be a number.") from None
    if not math.isfinite(result) or not minimum <= result <= maximum:
        raise ApiError(f"{key} must be between {minimum} and {maximum}.")
    return result


def boolean(data, key):
    if type(data.get(key)) is not bool:
        raise ApiError(f"{key} must be true or false.")
    return int(data[key])


def email(data):
    result = text(data, "email", maximum=254).lower()
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", result):
        raise ApiError("Enter a valid email address.")
    return result


def password(data):
    value = data.get("password", "")
    if not isinstance(value, str) or not 10 <= len(value) <= 128:
        raise ApiError("Password must be 10–128 characters.")
    return value
