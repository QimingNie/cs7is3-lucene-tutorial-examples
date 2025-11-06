#!/usr/bin/env bash
set -euo pipefail

INDEX_DIR="${1:-../cran_index_1030}"         
QUERY_FILE="${2:-../cran.qry}"               
OUTPUT_DIR="${3:-runs}"                       
QRELS="${4:-../QRelsCorrectedforTRECeval}"   
MAX_HITS="${5:-1000}"                        

MAIN_CLASS="CranfieldSearcher"               
MODELS=("bm25" "classic" "vsm" "lm" "dfr")    


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

  # head -n 3 "${run_file}" || true


  local eval_file="${OUTPUT_DIR}/eval_${model}.txt"
  trec_eval "${QRELS}" "${run_file}" > "${eval_file}"

  echo "Eval file: ${eval_file}"
  print_metrics "${eval_file}"
}


for m in "${MODELS[@]}"; do
  run_model "${m}"
done


echo "==================== Summary (MAP / R-prec / P@10 / P@20 / nDCG@10 / nDCG@20) ===================="
printf "%-10s | %-8s | %-8s | %-8s | %-8s | %-10s | %-10s\n" "Model" "MAP" "R-prec" "P@10" "P@20" "nDCG@10" "nDCG@20"
printf -- "-------------------------------------------------------------------------------------------------\n"
for m in "${MODELS[@]}"; do
  case "${m}" in
    vsm) eval_file="${OUTPUT_DIR}/eval_classic.txt" ;; # vsm 用 classic 的文件
    *)   eval_file="${OUTPUT_DIR}/eval_${m}.txt" ;;
  esac
  map=$(grep -E '^map[[:space:]]+all'         "${eval_file}" | awk '{print $3}')
  rpr=$(grep -E '^Rprec[[:space:]]+all'       "${eval_file}" | awk '{print $3}')
  p10=$(grep -E '^P_10[[:space:]]+all'        "${eval_file}" | awk '{print $3}')
  p20=$(grep -E '^P_20[[:space:]]+all'        "${eval_file}" | awk '{print $3}')
  ndc10=$(grep -E '^ndcg_cut_10[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  ndc20=$(grep -E '^ndcg_cut_20[[:space:]]+all' "${eval_file}" | awk '{print $3}')
  printf "%-10s | %-8s | %-8s | %-8s | %-8s | %-10s | %-10s\n" "${m}" "${map:-NA}" "${rpr:-NA}" "${p10:-NA}" "${p20:-NA}" "${ndc10:-NA}" "${ndc20:-NA}"
done
echo "================================================================================================="

echo "Done. Full outputs under: ${OUTPUT_DIR}"

