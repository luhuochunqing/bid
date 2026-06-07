FROM python:3.11-slim

# System dependencies for markitdown (PDF, image OCR support)
RUN apt-get update && apt-get install -y --no-install-recommends \
        # pdfminer / markitdown PDF support
        libpoppler-cpp-dev \
        # Tesseract OCR (optional — enables scanned-PDF extraction)
        tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for security
RUN useradd --create-home --shell /bin/bash sidecar
WORKDIR /app

# Install Python dependencies before copying app code (layer cache)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY app.py converter.py ./

# Switch to non-root user
USER sidecar

EXPOSE 8000

# SIDECAR_HOST is overridden to 0.0.0.0 inside Docker so the port
# is reachable from outside the container. SIDECAR_SHARED_KEY must
# be injected via -e or a secrets manager in production.
ENV SIDECAR_HOST=0.0.0.0 \
    SIDECAR_PORT=8000

CMD ["sh", "-c", "uvicorn app:app --host ${SIDECAR_HOST} --port ${SIDECAR_PORT}"]
