FROM python:3.12-alpine
WORKDIR /fixture
COPY scripts/fixtures/smtp/server.py server.py
USER nobody
CMD ["python3", "server.py"]
