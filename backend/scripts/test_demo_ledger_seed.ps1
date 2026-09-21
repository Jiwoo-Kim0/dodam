param(
    [string]$Container = 'dodam-679-mysql',
    [string]$Database = 'dodam_679_test'
)

$ErrorActionPreference = 'Stop'
if ($Container -ne 'dodam-679-mysql' -or $Database -ne 'dodam_679_test') {
    throw 'This regression runner only supports the disposable 679 test database.'
}
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$seedPath = Join-Path $PSScriptRoot '../database/seeds/DODAM_DEMO_U0003_LEDGER_679_SEED.sql'

function Invoke-Sql([string]$Sql) {
    $result = $Sql | docker exec -i $Container sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --default-character-set=utf8mb4 -uroot -N dodam_679_test'
    if ($LASTEXITCODE -ne 0) { throw 'Test SQL failed.' }
    return $result
}

function Invoke-Seed {
    $result = docker exec $Container sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql --default-character-set=utf8mb4 -uroot dodam_679_test < /tmp/dodam-679-seed.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Seed execution failed.' }
    if (@($result | Where-Object { $_ -eq "DODAM_679_READY`t1" }).Count -ne 1) {
        throw 'Seed preflight did not pass.'
    }
    return $result
}

function Assert-SeedData {
    $monthly = @(Invoke-Sql @'
SELECT CONCAT_WS('|', MIN(DATE_FORMAT(t.transaction_date, '%Y-%m')),
 SUM(t.transaction_id LIKE '%-FIX-%'), SUM(IF(t.transaction_id LIKE '%-FIX-%', t.amount, 0)),
 SUM(t.transaction_id LIKE '%-SAV-%'), SUM(IF(t.transaction_id LIKE '%-SAV-%', t.amount, 0)),
 SUM(t.transaction_id LIKE '%-TRF-%'), SUM(IF(t.transaction_id LIKE '%-TRF-%', t.amount, 0)),
 SUM(t.transaction_id LIKE '%-INC-01'), SUM(IF(t.transaction_id LIKE '%-INC-01', t.amount, 0)))
FROM `transaction` t JOIN `user` u ON u.user_id = t.user_id
WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND t.source_type = 'SEED'
 AND t.transaction_date BETWEEN '2026-03-01' AND '2026-09-30'
GROUP BY DATE_FORMAT(t.transaction_date, '%Y-%m') ORDER BY DATE_FORMAT(t.transaction_date, '%Y-%m');
'@)
    $fixedTotals = @(1478831, 1443831, 1425831, 1429831, 1448831, 1460831, 1439831)
    if ($monthly.Count -ne 7) { throw 'Expected seven ledger months.' }
    for ($i = 0; $i -lt 7; $i++) {
        $refund = if ($i -eq 0) { '1|412350' } else { '0|0' }
        $expected = ('2026-{0:00}|10|{1}|3|1600000|1|300000|{2}' -f ($i + 3), $fixedTotals[$i], $refund)
        if ($monthly[$i] -ne $expected) { throw "Ledger expectation failed: $($monthly[$i])" }
    }
    $bills = @(Invoke-Sql @'
SELECT CONCAT_WS('|', b.billing_month, b.total_balance, b.status)
FROM card_billing_statement b
WHERE b.card_id = (SELECT MIN(c.card_id) FROM card c JOIN `user` u ON u.user_id = c.user_id
 JOIN account a ON a.account_id = c.withdrawal_account_id
 WHERE u.email = 'seed.kmeans.u0003@dodam.invalid' AND a.account_type = 'DEMAND_DEPOSIT'
   AND a.status = 'ACTIVE' AND a.source_type = 'SEED')
 AND b.billing_month BETWEEN '2026-04' AND '2026-10'
ORDER BY b.billing_month;
'@)
    $billTotals = @(835890, 815890, 1563190, 909590, 921790, 891390, 845390)
    if ($bills.Count -ne 7) { throw 'Expected seven card bills.' }
    for ($i = 0; $i -lt 7; $i++) {
        $status = if ($i -eq 6) { 'SCHEDULED' } else { 'PAID' }
        $expected = '2026-{0:00}|{1}|{2}' -f ($i + 4), $billTotals[$i], $status
        if ($bills[$i] -ne $expected) { throw "Billing expectation failed: $($bills[$i])" }
    }
}

function Get-Fingerprint {
    # 계좌 암호문이 출력되지 않도록 일회용 컨테이너 내부에서 덤프를 해시한다.
    $result = docker exec $Container sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqldump -uroot --no-tablespaces --skip-comments --skip-extended-insert --order-by-primary --no-create-info dodam_679_test transaction card card_billing_statement financial_profile account loan_repayment savings_payment transaction_category > /tmp/dodam-679-snapshot.sql && sha256sum /tmp/dodam-679-snapshot.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot failed.' }
    return $result
}

docker cp $seedPath "${Container}:/tmp/dodam-679-seed.sql"
if ($LASTEXITCODE -ne 0) { throw 'Seed copy failed.' }
$null = Invoke-Seed
Assert-SeedData
Write-Output 'PASS: preflight and all seven months of ledger amounts and card bills match.'
$before = Get-Fingerprint
$null = Invoke-Seed
Assert-SeedData
if ($before -ne (Get-Fingerprint)) { throw 'Rerun changed permanent data.' }
Write-Output 'PASS: rerun preserves every dumped row and ID.'

$uid = '(SELECT user_id FROM `user` WHERE email = ''seed.kmeans.u0003@dodam.invalid'')'
$savingIds = @(Invoke-Sql "SELECT account_id FROM account WHERE user_id = $uid AND account_type = 'SAVINGS' AND status = 'ACTIVE';") -join ','
if ($savingIds -notmatch '^\d+(,\d+)*$') { throw 'Active savings fixture is missing.' }
$paymentId = Invoke-Sql 'SELECT COALESCE(MAX(savings_payment_id), 0) + 1 FROM savings_payment;'
$repaymentId = Invoke-Sql 'SELECT COALESCE(MAX(loan_repayment_id), 0) + 1 FROM loan_repayment;'
$cardId = Invoke-Sql "SELECT MIN(card_id) FROM card WHERE user_id = $uid AND withdrawal_account_id IN (SELECT account_id FROM account WHERE user_id = $uid AND account_type = 'DEMAND_DEPOSIT' AND status = 'ACTIVE' AND source_type = 'SEED');"
$originalCardDay = Invoke-Sql "SELECT withdrawal_day FROM card WHERE card_id = $cardId;"
$paymentFixture = @"
INSERT INTO savings_payment (savings_payment_id, deposit_saving_id, account_type, payment_date, amount, installment_number, status)
SELECT $paymentId, deposit_saving_id, account_type, payment_date, amount,
 (SELECT MAX(p2.installment_number) + 1 FROM savings_payment p2 WHERE p2.deposit_saving_id = p.deposit_saving_id), 'PAID'
FROM savings_payment p WHERE deposit_saving_id IN ($savingIds) AND status = 'PAID' AND payment_date = '2026-03-15'
ORDER BY savings_payment_id LIMIT 1;
"@
$cases = @(
    @{
        Name = 'missing active savings account'
        Change = "UPDATE account SET status = 'CLOSED' WHERE account_id IN ($savingIds);"
        Restore = "UPDATE account SET status = 'ACTIVE' WHERE account_id IN ($savingIds);"
    },
    @{
        Name = 'loan repayment mismatch'
        Change = "UPDATE loan_repayment SET amount = amount + 1 WHERE loan_id IN (SELECT loan_id FROM loan WHERE user_id = $uid) AND status = 'PAID';"
        Restore = "UPDATE loan_repayment SET amount = amount - 1 WHERE loan_id IN (SELECT loan_id FROM loan WHERE user_id = $uid) AND status = 'PAID';"
    },
    @{
        Name = 'missing active category'
        Change = "UPDATE transaction_category SET active = 0 WHERE code = 'HOUSING';"
        Restore = "UPDATE transaction_category SET active = 1 WHERE code = 'HOUSING';"
    },
    @{
        Name = 'duplicate savings date with another installment number'
        Change = $paymentFixture
        Restore = "DELETE FROM savings_payment WHERE savings_payment_id = $paymentId;"
    },
    @{
        Name = 'extra loan repayment with another amount'
        Change = "INSERT INTO loan_repayment (loan_repayment_id, loan_id, repayment_date, amount, status) SELECT $repaymentId, loan_id, repayment_date, amount + 1, status FROM loan_repayment WHERE loan_id IN (SELECT loan_id FROM loan WHERE user_id = $uid) AND status = 'PAID' ORDER BY repayment_date LIMIT 1;"
        Restore = "DELETE FROM loan_repayment WHERE loan_repayment_id = $repaymentId;"
    },
    @{
        Name = 'wrong card withdrawal day (no-op must fail)'
        Change = "UPDATE card SET withdrawal_day = 16 WHERE card_id = $cardId;"
        Restore = "UPDATE card SET withdrawal_day = $originalCardDay WHERE card_id = $cardId;"
    }
)

foreach ($case in $cases) {
    $changed = $false
    try {
        $null = Invoke-Sql $case.Change
        $changed = $true
        $failedInput = Get-Fingerprint
        $rejected = $false
        try { $null = Invoke-Seed } catch {
            if ($_.Exception.Message -ne 'Seed preflight did not pass.') { throw }
            $rejected = $true
        }
        if (-not $rejected) { throw "Invalid input was accepted: $($case.Name)" }
        if ($failedInput -ne (Get-Fingerprint)) { throw "Preflight isolation failed: $($case.Name)" }
        Write-Output "PASS: $($case.Name) causes no permanent writes."
    } finally {
        if ($changed) { $null = Invoke-Sql $case.Restore }
    }
}
if ($before -ne (Get-Fingerprint)) { throw 'Failure fixtures were not restored.' }
$scratch = Invoke-Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'tmp_dodam_679_%';"
if ($scratch -ne '0') { throw 'Scratch tables remain.' }
Write-Output 'PASS: failure fixtures restored; scratch tables absent.'
