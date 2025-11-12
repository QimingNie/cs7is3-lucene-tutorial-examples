#!/usr/bin/env bash
set -euo pipefail
export JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector"
INDEX_DIR="${1:-../cran_index_1030}"         
QUERY_FILE="${2:-../cran.qry}"               
OUTPUT_DIR="${3:-runs}"                       
QRELS="${4:-../QRelsCorrectedforTRECeval}"   
MAX_HITS="${5:-1000}"                        

MAIN_CLASS="CranfieldSearcher"               
MODELS=("bm25" "vsm" "lm" "dfr")    


mkdir -p "${OUTPUT_DIR}"


print_metrics() {
  local eval_file="$1"

  local metrics=(
    "map" "gm_map"          
    "Rprec"
    "bpref"
    "recip_rank"
    "P_5" "P_10" "P_20" "P_30"
    "recall_5" "recall_10" "recall_20" "recall_100" "recall_1000"
    "ndcg_cut_5" "ndcg_cut_10" "ndcg_cut_20" "ndcg_cut_100"
    "num_ret" "num_rel" "num_rel_ret"
  )

  echo "Key metrics:"
  for m in "${metrics[@]}"; do
    
    if grep -qE "^${m}[[:space:]]+all[[:space:]]+" "${eval_file}"; then
      grep -E "^${m}[[:space:]]+all[[:space:]]+" "${eval_file}"
    fi
  done
  echo
}

run_model() {
  local model="$1"
  echo "==================== Running model: ${model} ===================="


  mvn -q exec:java -Dexec.mainClass="${MAIN_CLASS}" \
    -Dexec.args="${INDEX_DIR} ${QUERY_FILE} ${OUTPUT_DIR} --model=${model} --maxHits=${MAX_HITS}" \
    -Dexec.jvmArgs="--enable-native-access=ALL-UNNAMED --add-modules jdk.incubator.vector"


  local run_file=""
  case "${model}" in
    bm25)    run_file="${OUTPUT_DIR}/run_bm25.txt" ;;
    vsm)     run_file="${OUTPUT_DIR}/run_classic.txt" ;;
    lm)      run_file="${OUTPUT_DIR}/run_lm.txt" ;;
    dfr)     run_file="${OUTPUT_DIR}/run_dfr.txt" ;;
    *)       run_file="${OUTPUT_DIR}/run_${model}.txt" ;;
  esac

  if [[ ! -s "${run_file}" ]]; then
    echo "ERROR: run file not found or empty: ${run_file}" >&2
    exit 1
  fi

  echo "Run file: ${run_file}  (size: $(wc -l < "${run_file}") lines)"

  # head -n 3 "${run_file}" || true


  local eval_file="${OUTPUT_DIR}/eval_${model}.txt"
  trec_eval "${QRELS}" "${run_file}" > "${eval_file}"

  echo "Eval file: ${eval_file}"
  print_metrics "${eval_file}"
}


for m in "${MODELS[@]}"; do
  run_model "${m}"
done

echo "Done. Full outputs under: ${OUTPUT_DIR}"

