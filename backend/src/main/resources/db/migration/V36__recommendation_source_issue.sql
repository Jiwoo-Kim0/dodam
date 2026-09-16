-- Jira: S15P21D105-628 (추천-진단 문제 연결 sourceIssueId)
-- Schema basis: V18 recommendation, V17 diagnosis_issue.
--
-- 범위: 추천이 어느 진단 문제에서 만들어졌는지 저장한다. 547·548은 생성 후보(RecommendationCandidate.sourceIssueId)까지만
-- 문제 id를 들고 있었고 저장 컬럼이 없어 버려졌다. 627로 같은 진단에 같은 domain/type 문제가 여러 개 생기면서
-- (카테고리별·대출별) FE가 정렬 순서로는 문제-추천을 대응시킬 수 없게 됐다.
--
-- NULL 허용: 기존 행은 채우지 않는다(재생성해도 같은 generation_key라 기존 행을 돌려주므로 값이 생기지 않는다).
-- 여러 문제를 합쳐 만든 후보도 null이다. 진단 일치(문제가 같은 diagnosis_id에 속함)와 소유권은 diagnosis_issue에
-- (diagnosis_id, issue_id) UNIQUE가 없어 복합 FK로 걸 수 없고, RecommendationStore가 저장 전에 검증한다.
-- 완료 진단의 문제 행은 삭제하지 않으므로(미완료 정리만 삭제) 단일 FK로 충분하다.

ALTER TABLE `recommendation`
  ADD COLUMN `source_issue_id` BIGINT NULL AFTER `goal_id`,
  ADD KEY `idx_recommendation_user_source_issue` (`user_id`, `source_issue_id`),
  ADD CONSTRAINT `fk_recommendation_source_issue`
    FOREIGN KEY (`source_issue_id`) REFERENCES `diagnosis_issue` (`issue_id`);
