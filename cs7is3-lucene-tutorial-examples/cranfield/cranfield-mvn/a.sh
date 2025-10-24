#!/usr/bin/env bash
set -euo pipefail

# ------------------ 配置区（按需修改） ------------------
INDEX_DIR="${1:-../cran_index}"               # Lucene 索引目录
QUERY_FILE="${2:-../cran.qry}"                # Cranfield 查询文件
OUTPUT_DIR="${3:-runs}"                       # 输出目录（run_* 和 eval_*）
QRELS="${4:-../QRelsCorrectedforTRECeval}"    # TREC qrels 文件
MAX_HITS="${5:-1000}"                         # 每个查询返回的最大文档数

# Maven 主类（保持与你工程一致）
MAIN_CLASS="CranfieldSearcher"

# 要跑的模型列表（顺序可改）
MODELS=("bm25" "classic" "vsm" "lm" "dfr")
# -------------------------------------------------------

mkdir -p "${OUTPUT_DIR}"

run_model() {
  local model="$1"
  echo "==================== Running model: ${model} ===================="

  # 执行搜索（你的 CranfieldSearcher 同时支持 --model= & 位置参数）
  mvn -q exec:java -Dexec.mainClass="${MAIN_CLASS}" \
    -Dexec.args="${INDEX_DIR} ${QUERY_FILE} ${OUTPUT_DIR} --model=${model} --maxHits=${MAX_HITS}"

  # 规范 run 文件名（与 CranfieldSearcher 中一致）
  local run_file=""
  case "${model}" in
    bm25)    run_file="${OUTPUT_DIR}/run_bm25.txt" ;;
    classic) run_file="${OUTPUT_DIR}/run_classic.txt" ;;
    vsm)     run_file="${OUTPUT_DIR}/run_classic.txt" ;; # vsm 归入 classic 文件名
    lm)      run_file="${OUTPUT_DIR}/run_lm.txt" ;;
    dfr)     run_file="${OUTPUT_DIR}/run_dfr.txt" ;;
    *)       run_file="${OUTPUT_DIR}/run_${model}.txt" ;;
  esac

  if [[ ! -s "${run_file}" ]]; then
    echo "ERROR: run file not found or empty: ${run_file}" >&2
    exit 1
  fi

  echo "Run file: ${run_file}  (size: $(wc -l < "${run_file}") lines)"
  head -n 3 "${run_file}" || true

  # 评测并保存完整输出
  local eval_file="${OUTPUT_DIR}/eval_${model}.txt"
  trec_eval "${QRELS}" "${run_file}" > "${eval_file}"

  echo "Eval file: ${eval_file}"
  echo "Key metrics:"
  grep -E '^(map|Rprec|P_10|ndcg_cut_10)[[:space:]]' "${eval_file}" || true
  echo
}

# 跑所有模型
for m in "${MODELS[@]}"; do
  run_model "${m}"
done

# 汇总报告
echo "==================== Summary (MAP / R-prec / P@10 / nDCG@10) ===================="
printf "%-10s | %-8s | %-8s | %-8s | %-10s\n" "Model" "MAP" "R-prec" "P@10" "nDCG@10"
printf -- "-----------------------------------------------------------------------\n"
for m in "${MODELS[@]}"; do
  case "${m}" in
    vsm) eval_file="${OUTPUT_DIR}/eval_classic.txt" ;; # vsm 用 classic 的文件
    *)   eval_file="${OUTPUT_DIR}/eval_${m}.txt" ;;
  esac
  map=$(grep -E '^map[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  rpr=$(grep -E '^Rprec[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  p10=$(grep -E '^P_10[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  ndc=$(grep -E '^ndcg_cut_10[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  printf "%-10s | %-8s | %-8s | %-8s | %-10s\n" "${m}" "${map:-NA}" "${rpr:-NA}" "${p10:-NA}" "${ndc:-NA}"
done
echo "==============================================================================="

# 结束
echo "Done. Full outputs under: ${OUTPUT_DIR}"
