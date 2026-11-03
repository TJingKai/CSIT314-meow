FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt gunicorn
COPY . .
RUN python -m scripts.seed
EXPOSE 8000
CMD ["gunicorn", "-b", "0.0.0.0:8000", "app.boundary.api:create_app()"]
