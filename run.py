"""Run locally with Waitress; never enable the Flask debug server."""

import argparse

from waitress import serve

from backend import create_app


def main():
    parser = argparse.ArgumentParser(description="MEOW interior design platform")
    parser.add_argument(
        "--demo",
        action="store_true",
        help="Seed fictional sample accounts on an empty database",
    )
    parser.add_argument(
        "--host",
        default="127.0.0.1",
        help="Bind address (default: local computer only)",
    )
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    config = {"SEED_DEMO": True} if args.demo else None
    app = create_app(config)
    print(f"MEOW is running at http://{args.host}:{args.port}", flush=True)
    if args.demo:
        print("Demo password: MEOWDemo!2026 (fictional accounts only)", flush=True)
    serve(app, host=args.host, port=args.port, threads=4)


if __name__ == "__main__":
    main()
