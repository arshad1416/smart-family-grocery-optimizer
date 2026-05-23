# Use high-performance slim python image
FROM python:3.10-slim

# Set work directory
WORKDIR /app

# Install system dependencies needed for compiling psycopg2 and standard curl utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend files
COPY . .

# Set production environment flags
ENV PYTHONUNBUFFERED=1
ENV PORT=8000

# Expose port
EXPOSE 8000

# Run server on Fargate startup
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT}"]
