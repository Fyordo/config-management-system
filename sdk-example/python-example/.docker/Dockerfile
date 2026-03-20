FROM python:3.13-slim AS base

RUN adduser --disabled-password --uid 1000 appuser

WORKDIR /app

COPY python-sdk/cms/ ./python-sdk/cms/
COPY sdk-example/python-example/main.py ./main.py

RUN mkdir -p /app/config && chown -R appuser:appuser /app

USER appuser

ENV PYTHONPATH=/app/python-sdk

EXPOSE 8080

ENV PYTHONUNBUFFERED=1

ENTRYPOINT ["python3", "-u", "main.py"]
