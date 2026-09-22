param(
    [string]$Container = 'dodam-679-mysql',
    [string]$Database = 'dodam_679_test'
)

$ErrorActionPreference = 'Stop'
if ($Container -ne 'dodam-679-mysql' -or $Database -ne 'dodam_679_test') {
    throw 'This regression runner only supports the disposable 679 test database.'
}
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$verifyPath = Join-Path $PSScriptRoot '../database/seeds/DODAM_DEMO_U0003_DERIVED_VERIFY_699.sql'
$resetPath = Join-Path $PSScriptRoot '../database/seeds/DODAM_DEMO_U0003_DERIVED_RESET_699.sql'

function Invoke-Sql([string]$Sql) {
    $result = $Sql | docker exec -i $Container sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --default-character-set=utf8mb4 -uroot -N dodam_679_test'
    if ($LASTEXITCODE -ne 0) { throw 'Test SQL failed.' }
    return @($result)
}

function Invoke-Reset {
    $result = docker exec $Container sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --default-character-set=utf8mb4 -uroot -N dodam_679_test < /tmp/dodam-699-reset.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Reset execution failed.' }
    return @($result)
}

function Add-ReportFixture([string]$Email, [string]$Title) {
    $sql = @"
INSERT INTO report (user_id, type, period, period_start, period_end, title, created_at)
SELECT user_id, 'MONTHLY', '2026-08', '2026-08-01', '2026-08-31', '$Title', NOW()
FROM ``user`` WHERE email = '$Email';
INSERT INTO monthly_report (report_id, report_type, ``year_month``, data_status, financial_summary, efficiency_summary)
SELECT report_id, type, period, 'READY', JSON_OBJECT(), JSON_OBJECT('score', 85)
FROM report WHERE type = 'MONTHLY' AND period = '2026-08'
 AND user_id = (SELECT user_id FROM ``user`` WHERE email = '$Email');
"@
    $null = Invoke-Sql $sql
}

function Get-ReportCount([string]$Email) {
    $result = Invoke-Sql @"
SELECT COUNT(*) FROM report r JOIN ``user`` u ON u.user_id = r.user_id
WHERE u.email = '$Email' AND r.type = 'MONTHLY' AND r.period = '2026-08';
"@
    return [int]($result | Select-Object -First 1)
}

$u3 = 'seed.kmeans.u0003@dodam.invalid'
$u4 = 'seed.kmeans.u0004@dodam.invalid'

docker cp $verifyPath "${Container}:/tmp/dodam-699-verify.sql"
if ($LASTEXITCODE -ne 0) { throw 'Verify SQL copy failed.' }
docker cp $resetPath "${Container}:/tmp/dodam-699-reset.sql"
if ($LASTEXITCODE -ne 0) { throw 'Reset SQL copy failed.' }

# The reset script is intentionally destructive only in this disposable database. Start from no report fixtures.
$null = Invoke-Sql @"
DELETE mr FROM monthly_report mr JOIN report r ON r.report_id = mr.report_id JOIN ``user`` u ON u.user_id = r.user_id
WHERE u.email IN ('$u3', '$u4') AND r.type = 'MONTHLY' AND r.period = '2026-08';
DELETE r FROM report r JOIN ``user`` u ON u.user_id = r.user_id
WHERE u.email IN ('$u3', '$u4') AND r.type = 'MONTHLY' AND r.period = '2026-08';
"@

$source = Invoke-Sql @"
SELECT CONCAT_WS('|',
 SUM(CASE WHEN t.transaction_type = 'INCOME' THEN t.amount ELSE 0 END),
 SUM(CASE WHEN t.transaction_type = 'EXPENSE' AND (t.spending_type IS NULL OR t.spending_type <> 'NON_SPENDING') THEN t.amount ELSE 0 END),
 SUM(CASE WHEN t.transaction_type = 'EXPENSE' AND t.spending_type = 'NON_SPENDING' THEN t.amount ELSE 0 END),
 SUM(CASE WHEN t.transaction_type = 'EXPENSE' AND t.spending_type = 'FIXED' THEN t.amount ELSE 0 END))
FROM ``transaction`` t JOIN ``user`` u ON u.user_id = t.user_id
WHERE u.email = '$u3' AND t.hidden = 0 AND t.transaction_date BETWEEN '2026-08-01' AND '2026-08-31';
"@
$sourceLine = $source | Select-Object -First 1
if ($sourceLine -ne '3012400|2559221|400000|1565221') {
    throw "Final 679 source contract is missing: $sourceLine"
}

Add-ReportFixture $u3 '699 U0003 isolation fixture'
Add-ReportFixture $u4 '699 U0004 isolation fixture'
$first = Invoke-Reset
if ($first[-1] -ne "1`t1`t1`t1") { throw "Unexpected first reset result: $($first[-1])" }
if ((Get-ReportCount $u3) -ne 0 -or (Get-ReportCount $u4) -ne 1) {
    throw 'User isolation failed.'
}
Write-Output 'PASS: reset deletes only the U0003 monthly report.'

$second = Invoke-Reset
if ($second[-1] -ne "1`t0`t0`t1") { throw "Unexpected rerun result: $($second[-1])" }
Write-Output 'PASS: reset is idempotent.'

Add-ReportFixture $u3 '699 preflight fixture'
$null = Invoke-Sql "UPDATE ``transaction`` SET amount = amount + 1 WHERE transaction_id = 'SEED-U0003-202608-FIX-01';"
try {
    $rejected = Invoke-Reset
    if ($rejected[-1] -ne "0`t0`t0`t1") { throw "Unexpected rejected result: $($rejected[-1])" }
    if ((Get-ReportCount $u3) -ne 1) { throw 'Failed preflight deleted the U0003 report.' }
    Write-Output 'PASS: a one-won source mismatch blocks every permanent delete.'
} finally {
    $null = Invoke-Sql "UPDATE ``transaction`` SET amount = amount - 1 WHERE transaction_id = 'SEED-U0003-202608-FIX-01';"
}

$cleanup = Invoke-Reset
if ($cleanup[-1] -ne "1`t1`t1`t1") { throw "Unexpected cleanup result: $($cleanup[-1])" }
$null = Invoke-Sql @"
DELETE mr FROM monthly_report mr JOIN report r ON r.report_id = mr.report_id JOIN ``user`` u ON u.user_id = r.user_id
WHERE u.email = '$u4' AND r.type = 'MONTHLY' AND r.period = '2026-08';
DELETE r FROM report r JOIN ``user`` u ON u.user_id = r.user_id
WHERE u.email = '$u4' AND r.type = 'MONTHLY' AND r.period = '2026-08';
"@
$scratch = Invoke-Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'tmp_dodam_699_%';"
if (($scratch | Select-Object -First 1) -ne '0') { throw 'Scratch tables remain.' }
Write-Output 'PASS: fixtures restored and scratch tables removed.'
