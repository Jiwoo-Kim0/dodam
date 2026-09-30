"""Compare the old and new SQL paths for a partial transaction page.

Requires a local MySQL container restored from exec/dodam_dump.sql.gz.
Runs both paths on one persistent mysql connection so process startup is excluded.
This measures SQL plus result transfer to the mysql client, not HTTP latency.
"""

import argparse
import json
import random
import statistics
import subprocess
import time


def percentile(values, percent):
    ordered = sorted(values)
    index = (len(ordered) - 1) * percent / 100
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (index - low)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", default="dodam-perf-mysql")
    parser.add_argument("--user-id", type=int, default=3)
    parser.add_argument("--month", default="2026-08")
    parser.add_argument("--repeats", type=int, default=100)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("--repeats must be positive")

    year, month = map(int, args.month.split("-"))
    next_year, next_month = (year + 1, 1) if month == 12 else (year, month + 1)
    start = f"{year:04d}-{month:02d}-01"
    end_exclusive = f"{next_year:04d}-{next_month:02d}-01"
    where = (f"WHERE entry.user_id = {args.user_id} AND entry.hidden = FALSE "
             f"AND entry.transaction_date >= '{start}' "
             f"AND entry.transaction_date < '{end_exclusive}'")
    count = ("SELECT COUNT(*) FROM `transaction` entry "
             "LEFT JOIN transaction_category category "
             "ON category.category_id = entry.category_id " + where + ";")
    select = ("SELECT entry.transaction_id, entry.account_id, entry.card_id, "
              "entry.category_id, category.code, category.name, "
              "entry.transaction_date, entry.transaction_time, entry.transaction_type, "
              "entry.amount, entry.balance_after, entry.merchant_name, "
              "entry.payment_method, entry.spending_type, entry.summary, entry.memo, "
              "entry.hidden, entry.source_type, entry.created_at, entry.updated_at "
              "FROM `transaction` entry LEFT JOIN transaction_category category "
              "ON category.category_id = entry.category_id " + where + " "
              "ORDER BY entry.transaction_date DESC, entry.transaction_time DESC, "
              "entry.transaction_id DESC LIMIT 100 OFFSET 0;")

    process = subprocess.Popen(
        ["docker", "exec", "-i", args.container, "mysql", "-uroot", "-N", "-B", "--quick", "--unbuffered", "dodam"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding="utf-8", errors="replace", bufsize=1,
    )
    marker = "__DODAM_BENCH_END__"

    def execute(sql):
        started = time.perf_counter_ns()
        process.stdin.write(sql + f"\nSELECT '{marker}';\n")
        process.stdin.flush()
        lines = []
        for line in process.stdout:
            if line.strip() == marker:
                return (time.perf_counter_ns() - started) / 1_000_000, lines
            lines.append(line)
        raise RuntimeError("mysql exited before the response marker")

    try:
        _, count_result = execute(count)
        total = int(count_result[-1].strip())
        if total >= 100:
            raise RuntimeError(f"chosen month has {total} visible rows; choose a month with <100")
        _, rows = execute(select)
        if len(rows) != total:
            raise RuntimeError(f"count={total}, selected rows={len(rows)}")

        samples = {"before_count_and_select": [], "after_select_only": []}
        randomizer = random.Random(20261001)
        for iteration in range(args.repeats + 20):
            modes = list(samples)
            randomizer.shuffle(modes)
            for mode in modes:
                sql = count + "\n" + select if mode == "before_count_and_select" else select
                elapsed, _ = execute(sql)
                if iteration >= 20:
                    samples[mode].append(elapsed)

        result = {
            "container": args.container,
            "user_id": args.user_id,
            "month": args.month,
            "visible_rows": total,
            "repeats_per_mode": args.repeats,
            "measurement": "persistent mysql client: SQL execution + result transfer + common sentinel query",
            "results_ms": {
                mode: {
                    "median": round(statistics.median(values), 3),
                    "p95": round(percentile(values, 95), 3),
                    "mean": round(statistics.mean(values), 3),
                }
                for mode, values in samples.items()
            },
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        process.stdin.close()
        process.terminate()
        process.wait(timeout=5)


if __name__ == "__main__":
    main()
