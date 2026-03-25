#!/usr/bin/env bash
# AWS connectivity diagnostic script for the Java RAG backend.
# Run from the repo root:  bash backend-java/diagnose-aws.sh
# Requires: curl, aws cli (optional but helpful)
# Loads credentials from backend-java/.env

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

# ── Load .env ───────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "=== AWS RAG Backend Connectivity Diagnostics ==="
echo "Date: $(date)"
echo ""

PASS=0
FAIL=0
WARN=0

check() {
  local label="$1"
  shift
  echo -n "[CHECK] $label ... "
  if output=$("$@" 2>&1); then
    echo "OK"
    ((PASS++))
    return 0
  else
    echo "FAIL"
    echo "       $output" | head -5
    ((FAIL++))
    return 1
  fi
}

warn_check() {
  local label="$1"
  shift
  echo -n "[CHECK] $label ... "
  if output=$("$@" 2>&1); then
    echo "OK"
    ((PASS++))
    return 0
  else
    echo "WARN (non-critical)"
    echo "       $output" | head -3
    ((WARN++))
    return 1
  fi
}

# ── 1. Environment variables ───────────────────────────────
echo "── 1. Environment Variables ──"
echo "   AWS_DEFAULT_REGION   = ${AWS_DEFAULT_REGION:-<unset>}"
echo "   BEDROCK_REGION       = ${BEDROCK_REGION:-<unset>}"
echo "   AWS_ACCESS_KEY_ID    = ${AWS_ACCESS_KEY_ID:0:8}..."
echo "   OPENSEARCH_ENDPOINT  = ${OPENSEARCH_ENDPOINT:-<unset>}"
echo "   S3_DOCUMENT_BUCKET   = ${S3_DOCUMENT_BUCKET:-<unset>}"
echo "   BDA_PROJECT_ARN      = ${BDA_PROJECT_ARN:-<unset>}"
echo "   RAG_ANSWER_MODEL_ID  = ${RAG_ANSWER_MODEL_ID:-<unset>}"
echo ""

# ── 2. Java backend health ─────────────────────────────────
echo "── 2. Java Backend Health ──"
check "GET /health on port 8001" \
  curl -sf --max-time 5 http://localhost:8001/health
echo ""

# ── 3. OpenSearch connectivity ──────────────────────────────
echo "── 3. OpenSearch Connectivity ──"
OS_AUTH=""
if [[ -n "${OPENSEARCH_USERNAME:-}" && -n "${OPENSEARCH_PASSWORD:-}" ]]; then
  OS_AUTH="-u ${OPENSEARCH_USERNAME}:${OPENSEARCH_PASSWORD}"
fi

check "OpenSearch cluster health" \
  curl -sf --max-time 10 $OS_AUTH "${OPENSEARCH_ENDPOINT}/_cluster/health"

warn_check "OpenSearch list indices" \
  curl -sf --max-time 10 $OS_AUTH "${OPENSEARCH_ENDPOINT}/_cat/indices?format=json&h=index,docs.count,health"
echo ""

# ── 4. S3 connectivity ─────────────────────────────────────
echo "── 4. S3 Connectivity ──"
if command -v aws &>/dev/null; then
  check "S3 bucket listing (head)" \
    aws s3 ls "s3://${S3_DOCUMENT_BUCKET}/" --region "${AWS_DEFAULT_REGION}" --max-items 3
else
  echo "   SKIP: aws cli not installed (install for S3 checks)"
  ((WARN++))
fi
echo ""

# ── 5. Bedrock model access ────────────────────────────────
echo "── 5. Bedrock Model Access ──"
BEDROCK_REGION_VAL="${BEDROCK_REGION:-${AWS_DEFAULT_REGION:-us-east-1}}"
ANSWER_MODEL="${RAG_ANSWER_MODEL_ID:-qwen.qwen3-235b-a22b-2507-v1:0}"

if command -v aws &>/dev/null; then
  # Test basic Bedrock connectivity by listing foundation models
  warn_check "Bedrock list-foundation-models ($BEDROCK_REGION_VAL)" \
    aws bedrock list-foundation-models \
      --region "$BEDROCK_REGION_VAL" \
      --query "modelSummaries[?modelId=='${ANSWER_MODEL}'].{id:modelId,status:modelLifecycle.status}" \
      --output table

  # Test invoke with a minimal payload
  echo -n "[CHECK] Bedrock invoke-model $ANSWER_MODEL ($BEDROCK_REGION_VAL) ... "
  PAYLOAD='{"messages":[{"role":"user","content":"hi"}],"max_tokens":10}'
  if invoke_output=$(aws bedrock-runtime invoke-model \
      --region "$BEDROCK_REGION_VAL" \
      --model-id "$ANSWER_MODEL" \
      --content-type "application/json" \
      --accept "application/json" \
      --body "$PAYLOAD" \
      /dev/stdout 2>&1); then
    echo "OK"
    echo "       Response preview: $(echo "$invoke_output" | head -c 200)"
    ((PASS++))
  else
    echo "FAIL"
    echo "       $invoke_output" | head -5
    ((FAIL++))
  fi

  # Test embedding model
  EMBED_MODEL="${RAG_EMBEDDING_MODEL_ID:-amazon.titan-embed-text-v1}"
  echo -n "[CHECK] Bedrock invoke-model $EMBED_MODEL ($BEDROCK_REGION_VAL) ... "
  EMBED_PAYLOAD='{"inputText":"test"}'
  if embed_output=$(aws bedrock-runtime invoke-model \
      --region "$BEDROCK_REGION_VAL" \
      --model-id "$EMBED_MODEL" \
      --content-type "application/json" \
      --accept "application/json" \
      --body "$EMBED_PAYLOAD" \
      /dev/stdout 2>&1); then
    echo "OK"
    ((PASS++))
  else
    echo "FAIL"
    echo "       $embed_output" | head -5
    ((FAIL++))
  fi
else
  echo "   SKIP: aws cli not installed (install for Bedrock checks)"
  ((WARN++))
fi
echo ""

# ── 6. PostgreSQL connectivity ──────────────────────────────
echo "── 6. PostgreSQL Connectivity ──"
DB_HOST_VAL="${DB_HOST:-localhost}"
DB_PORT_VAL="${DB_PORT:-5432}"
echo -n "[CHECK] TCP connect to ${DB_HOST_VAL}:${DB_PORT_VAL} ... "
if (echo > /dev/tcp/"$DB_HOST_VAL"/"$DB_PORT_VAL") 2>/dev/null; then
  echo "OK"
  ((PASS++))
else
  echo "FAIL"
  echo "       Cannot connect to PostgreSQL at ${DB_HOST_VAL}:${DB_PORT_VAL}"
  ((FAIL++))
fi
echo ""

# ── 7. Frontend .env check ──────────────────────────────────
echo "── 7. Frontend Configuration ──"
FRONTEND_ENV="$SCRIPT_DIR/../frontend/.env"
if [[ -f "$FRONTEND_ENV" ]]; then
  VITE_URL=$(grep "VITE_API_BASE_URL" "$FRONTEND_ENV" | cut -d= -f2-)
  echo "   VITE_API_BASE_URL = $VITE_URL"
  if [[ -z "$VITE_URL" ]]; then
    echo "   WARN: VITE_API_BASE_URL is empty"
    ((WARN++))
  else
    ((PASS++))
  fi
else
  echo "   FAIL: frontend/.env does not exist (VITE_API_BASE_URL will be undefined)"
  ((FAIL++))
fi
echo ""

# ── Summary ─────────────────────────────────────────────────
echo "=== Summary ==="
echo "   PASS: $PASS"
echo "   FAIL: $FAIL"
echo "   WARN: $WARN"
echo ""
if [[ $FAIL -gt 0 ]]; then
  echo "Some checks FAILED. Review the output above."
  exit 1
else
  echo "All critical checks passed."
  exit 0
fi
